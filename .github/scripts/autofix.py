#!/usr/bin/env python3
"""Auto-fix Kotlin/KSP/Hilt compile errors using Claude (single API call + retry-on-429)."""
import json, os, re, subprocess, sys, time, urllib.request, urllib.error
from pathlib import Path

LOG_PATH = "/tmp/build.log"
MODEL = "claude-sonnet-4-6"
MAX_TOKENS = 12000
LOG_TAIL_LINES = 200
MAX_FILES_IN_PROMPT = 600


def env(name, required=True):
    v = os.environ.get(name, "")
    if required and not v: sys.exit(f"missing env var: {name}")
    return v


def log(msg): print(f"[autofix] {msg}", flush=True)


def run(*args, check=True, capture=False):
    log(f"$ {' '.join(args)}")
    return subprocess.run(args, check=check, text=True, capture_output=capture)


def call_claude(api_key, system, user_prompt):
    is_oauth = api_key.startswith("sk-ant-oat")
    headers = {"anthropic-version": "2023-06-01", "content-type": "application/json"}
    if is_oauth:
        headers["Authorization"] = f"Bearer {api_key}"
        headers["anthropic-beta"] = "oauth-2025-04-20"
    else:
        headers["x-api-key"] = api_key
    body = json.dumps({
        "model": MODEL, "max_tokens": MAX_TOKENS, "system": system,
        "messages": [{"role": "user", "content": user_prompt}],
    }).encode()

    delays = [5, 15, 30, 60, 90]
    for attempt, delay in enumerate([0] + delays):
        if delay: log(f"sleeping {delay}s before retry {attempt}/{len(delays)}"); time.sleep(delay)
        req = urllib.request.Request("https://api.anthropic.com/v1/messages",
                                     data=body, method="POST", headers=headers)
        try:
            resp = urllib.request.urlopen(req, timeout=180)
            data = json.loads(resp.read())
            return "".join(b["text"] for b in data["content"] if b.get("type") == "text").strip()
        except urllib.error.HTTPError as e:
            err_body = e.read().decode()
            if e.code in (429, 529, 503) and attempt < len(delays):
                log(f"  HTTP {e.code} — will retry. {err_body[:200]}")
                continue
            sys.exit(f"Anthropic API error {e.code}: {err_body}")
    sys.exit("Out of retries")


def strip_fences(s):
    s = s.strip()
    if s.startswith("```"):
        s = re.sub(r"^```[a-zA-Z]*\n", "", s)
        s = re.sub(r"\n```\s*$", "", s)
    return s


def extract_log_tail(text):
    lines = text.splitlines()
    for i in range(len(lines) - 1, -1, -1):
        if "FAILURE:" in lines[i] or "BUILD FAILED" in lines[i]:
            start = max(0, i - LOG_TAIL_LINES // 2)
            end = min(len(lines), i + LOG_TAIL_LINES // 2)
            return "\n".join(lines[start:end])
    return "\n".join(lines[-LOG_TAIL_LINES:])


def find_kotlin_files(root="app/src/main"):
    return [str(p) for p in Path(root).rglob("*.kt")]


def post_slack(webhook, blocks, fallback):
    if not webhook: return
    try:
        urllib.request.urlopen(urllib.request.Request(
            webhook, data=json.dumps({"text": fallback, "blocks": blocks}).encode(),
            headers={"Content-Type": "application/json"}), timeout=15)
    except Exception as e:
        log(f"slack post failed: {e}")


def gh(method, path, data=None, token=None):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(f"https://api.github.com{path}", data=body, method=method, headers={
        "Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28", "Content-Type": "application/json", "User-Agent": "autofix",
    })
    try:
        resp = urllib.request.urlopen(req, timeout=30)
        return resp.status, json.loads(resp.read() or b"{}")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def main():
    api_key = env("ANTHROPIC_API_KEY")
    gh_token = env("GITHUB_TOKEN")
    repo = env("REPO")
    run_number = env("RUN_NUMBER")
    failed_sha = env("FAILED_SHA")
    failed_run_url = env("FAILED_RUN_URL")
    slack_webhook = env("SLACK_WEBHOOK_URL", required=False)

    if not Path(LOG_PATH).exists():
        log(f"{LOG_PATH} not found"); return 0

    log_tail = extract_log_tail(Path(LOG_PATH).read_text(errors="replace"))
    log(f"Log tail: {len(log_tail)} chars")

    project_files = find_kotlin_files()
    file_listing = "\n".join(project_files[:MAX_FILES_IN_PROMPT])

    # Single combined prompt: identify file(s) AND return the corrected content for each.
    # Reduces API call count from N+1 to 1 (saves on rate limit budget).
    prompt = (
        "You are an expert Android / Kotlin / Hilt / KSP engineer. A Gradle build "
        "failed. Below is the failure tail of the build log and a list of all "
        "Kotlin files in the project. Identify which file(s) need to be edited "
        "to fix the compile/KSP error, then return the corrected complete contents "
        "for each file.\n\n"
        "Heuristics for finding the file:\n"
        "  - `e: file:///.../X.kt:N:M ...` — direct kotlinc error\n"
        "  - `e: [ksp] ...` + 'Dependency trace:' naming a class FQN — map to file path\n"
        "  - 'Cannot find symbol' / 'Unresolved reference'\n\n"
        "Make the MINIMUM change to resolve the error. Preserve all logic, formatting, "
        "and comments. Add or restore missing imports. Do not refactor.\n\n"
        "Output a JSON object on a SINGLE LINE first, then a separator line `---`, then "
        "for each file the file path on its own line followed by `<<<EOF` then the file "
        "content then `EOF`. Example:\n\n"
        '{"files": ["app/src/main/.../X.kt"]}\n'
        "---\n"
        "app/src/main/.../X.kt\n"
        "<<<EOF\n"
        "package ...\nclass X { ... }\n"
        "EOF\n\n"
        "Use that exact structure. No markdown fences anywhere.\n\n"
        f"BUILD LOG TAIL:\n{log_tail}\n\n"
        f"PROJECT FILES (paths):\n{file_listing}"
    )
    raw = call_claude(api_key,
        "Expert Android build engineer. Be concise. Output exactly the requested format.",
        prompt)

    # Parse: first line = JSON; everything after first --- = bodies
    # Tolerate small variations: find a JSON object on the first non-empty line.
    parts = raw.split("\n---\n", 1)
    if len(parts) != 2:
        log("Response missing --- separator; raw[:600]:")
        log(raw[:600])
        post_slack(slack_webhook,
            [{"type": "section", "text": {"type": "mrkdwn",
              "text": f":warning: *Auto-fix model output unparseable* for build #{run_number}.\n"
                      f"<{failed_run_url}|View logs>"}}],
            "auto-fix output unparseable")
        return 0
    header_part, body_part = parts
    try:
        header_json_line = next(ln for ln in header_part.splitlines() if ln.strip().startswith("{"))
        meta = json.loads(header_json_line.strip())
        target_files = list(meta.get("files", []))
    except Exception as e:
        log(f"Header JSON parse failed: {e}; header={header_part[:200]}")
        return 0

    target_files = [f for f in target_files if Path(f).exists()][:5]
    if not target_files:
        log("No target files exist")
        post_slack(slack_webhook,
            [{"type": "section", "text": {"type": "mrkdwn",
              "text": f":warning: *Auto-fix could not identify a fixable file* for build #{run_number}.\n"
                      f"<{failed_run_url}|View logs>"}}],
            "auto-fix no fixable file")
        return 0
    log(f"Target files: {target_files}")

    # Parse bodies: split on lines exactly equal to one of the file paths followed by <<<EOF
    fixes = {}
    cursor = 0
    body_lines = body_part.splitlines()
    while cursor < len(body_lines):
        line = body_lines[cursor].strip()
        if line in target_files and cursor + 1 < len(body_lines) and body_lines[cursor + 1].strip() == "<<<EOF":
            path = line
            cursor += 2
            content_lines = []
            while cursor < len(body_lines) and body_lines[cursor].rstrip() != "EOF":
                content_lines.append(body_lines[cursor])
                cursor += 1
            fixes[path] = "\n".join(content_lines) + "\n"
            cursor += 1  # skip EOF
        else:
            cursor += 1

    if not fixes:
        log("Could not parse any file bodies")
        post_slack(slack_webhook,
            [{"type": "section", "text": {"type": "mrkdwn",
              "text": f":warning: *Auto-fix model returned no parseable file bodies* for build #{run_number}.\n"
                      f"<{failed_run_url}|View logs>"}}],
            "auto-fix returned no bodies")
        return 0

    run("git", "config", "user.name",  "galleryapp-autofix[bot]")
    run("git", "config", "user.email", "autofix@noreply.github.com")
    branch = f"auto-fix/build-{run_number}-{failed_sha[:7]}"
    res = run("git", "ls-remote", "--exit-code", "--heads", "origin", branch,
              check=False, capture=True)
    if res.returncode == 0:
        branch = f"{branch}-{os.urandom(2).hex()}"
    run("git", "checkout", "-b", branch)

    fixed = []
    for path, content in fixes.items():
        original = Path(path).read_text() if Path(path).exists() else ""
        if content == original:
            log(f"  no change for {path}"); continue
        Path(path).write_text(content)
        run("git", "add", path)
        fixed.append(path)
        log(f"  patched {path}")

    if not fixed:
        log("No actual changes after writing"); return 0

    files_block = "\n  - " + "\n  - ".join(fixed)
    commit_msg = (f"auto-fix: resolve compile errors from build #{run_number}\n\n"
                  f"Failing commit: {failed_sha[:7]}\nFailing run: {failed_run_url}\n\n"
                  f"Files patched:{files_block}")
    run("git", "commit", "-m", commit_msg)
    run("git", "push", "origin", branch)

    pr_body = (f"Auto-generated by `.github/workflows/build-and-notify.yml` after "
               f"build #{run_number} failed on `{failed_sha[:7]}`.\n\n"
               f"**Failing run:** {failed_run_url}\n\n"
               f"**Files patched:**\n```{files_block}\n```\n\n"
               f"> :warning: Patch generated by an LLM. Review carefully.")
    code, pr = gh("POST", f"/repos/{repo}/pulls", {
        "title": f"auto-fix: resolve build #{run_number} compile errors",
        "head": branch, "base": "main", "body": pr_body,
        "maintainer_can_modify": True,
    }, token=gh_token)
    if code not in (200, 201):
        log(f"PR creation failed {code}: {pr}")
        post_slack(slack_webhook,
            [{"type": "section", "text": {"type": "mrkdwn",
              "text": f":x: *Auto-fix branch pushed but PR open failed* for build #{run_number}.\n"
                      f"branch: `{branch}` <{failed_run_url}|build logs>"}}],
            "auto-fix PR open failed")
        return 1

    pr_url = pr["html_url"]; pr_number = pr["number"]
    log(f"PR #{pr_number} opened: {pr_url}")
    gh("POST", f"/repos/{repo}/issues/{pr_number}/labels",
       {"labels": ["auto-fix"]}, token=gh_token)

    post_slack(slack_webhook, [
        {"type": "header", "text": {"type": "plain_text",
          "text": f":hammer_and_wrench: Auto-fix PR for build #{run_number}"}},
        {"type": "section", "text": {"type": "mrkdwn",
          "text": f"Build #{run_number} on `{failed_sha[:7]}` failed compile. "
                  f"Patched {len(fixed)} file(s) and opened PR for review."}},
        {"type": "section", "fields": [
            {"type": "mrkdwn", "text": f"*PR*\n<{pr_url}|#{pr_number}>"},
            {"type": "mrkdwn", "text": f"*Branch*\n`{branch}`"},
            {"type": "mrkdwn", "text": f"*Failing run*\n<{failed_run_url}|logs>"},
            {"type": "mrkdwn", "text": f"*Files*\n{', '.join(fixed)}"},
        ]},
        {"type": "actions", "elements": [
            {"type": "button", "style": "primary",
             "text": {"type": "plain_text", "text": "Review PR"}, "url": pr_url}
        ]},
    ], f"Auto-fix PR #{pr_number} opened")
    return 0


if __name__ == "__main__":
    sys.exit(main())
