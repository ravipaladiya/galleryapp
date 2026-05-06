#!/usr/bin/env python3
"""Auto-fix Kotlin/KSP/Hilt compile errors using Claude."""
import json, os, re, subprocess, sys, urllib.request, urllib.error
from pathlib import Path

LOG_PATH = "/tmp/build.log"
MODEL = "claude-sonnet-4-6"
MAX_TOKENS = 8000
LOG_TAIL_LINES = 200


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
    req = urllib.request.Request("https://api.anthropic.com/v1/messages",
                                 data=body, method="POST", headers=headers)
    try:
        resp = urllib.request.urlopen(req, timeout=120)
        data = json.loads(resp.read())
    except urllib.error.HTTPError as e:
        sys.exit(f"Anthropic API error {e.code}: {e.read().decode()}")
    return "".join(b["text"] for b in data["content"] if b.get("type") == "text").strip()


def strip_fences(s):
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

    log_text = Path(LOG_PATH).read_text(errors="replace")
    log_tail = extract_log_tail(log_text)
    log(f"Log tail: {len(log_tail)} chars")

    project_files = find_kotlin_files()
    file_listing = "\n".join(project_files[:500])

    identify_prompt = (
        "Below is the failing tail of a Gradle Android build log, followed by all "
        "Kotlin source files in the project. Identify which source file(s) need to "
        "be edited to fix the compile/KSP error.\n\n"
        "Heuristics: look for `e: file:///.../X.kt:N:M` (kotlinc), "
        "`e: [ksp] ...` + 'Dependency trace:' (Hilt/KSP — map class FQN to path), "
        "'Cannot find symbol' / 'Unresolved reference'.\n\n"
        "Return ONLY a JSON array of repo-relative file paths (no fences, no prose).\n\n"
        f"BUILD LOG TAIL:\n```\n{log_tail}\n```\n\n"
        f"PROJECT FILES:\n{file_listing}"
    )
    raw = strip_fences(call_claude(api_key,
        "You are an expert Android build engineer.", identify_prompt))
    log(f"Identify response: {raw[:400]}")
    try:
        target_files = json.loads(raw)
        assert isinstance(target_files, list)
    except Exception as e:
        log(f"Could not parse file list ({e}); aborting")
        post_slack(slack_webhook,
            [{"type": "section", "text": {"type": "mrkdwn",
              "text": f":warning: *Auto-fix could not identify failing file* for build #{run_number}.\n"
                      f"<{failed_run_url}|View logs>"}}],
            "auto-fix could not identify failing file")
        return 0

    target_files = [f for f in target_files if Path(f).exists()][:5]
    if not target_files:
        log("No target files exist"); return 0
    log(f"Target files: {target_files}")

    run("git", "config", "user.name",  "galleryapp-autofix[bot]")
    run("git", "config", "user.email", "autofix@noreply.github.com")
    branch = f"auto-fix/build-{run_number}-{failed_sha[:7]}"
    res = run("git", "ls-remote", "--exit-code", "--heads", "origin", branch,
              check=False, capture=True)
    if res.returncode == 0:
        branch = f"{branch}-{os.urandom(2).hex()}"
    run("git", "checkout", "-b", branch)

    fixed = []
    for path in target_files:
        original = Path(path).read_text()
        fix_prompt = (
            f"You are fixing the Kotlin/Hilt/KSP compile error in this single file. "
            f"Make the MINIMUM change to resolve the error in the build log. Preserve "
            f"all logic, formatting, and comments. Add/restore missing imports. Do not "
            f"refactor.\n\n"
            f"File: `{path}`\n\n"
            f"Build log (failure tail):\n```\n{log_tail}\n```\n\n"
            f"Current file contents:\n```kotlin\n{original}\n```\n\n"
            f"Respond with ONLY the corrected complete file contents. No fences, no commentary."
        )
        fixed_content = strip_fences(call_claude(api_key,
            "You are an expert Kotlin / Android Jetpack Compose / Hilt engineer.",
            fix_prompt))
        if not fixed_content.strip() or fixed_content == original:
            log(f"  no change for {path}"); continue
        Path(path).write_text(fixed_content)
        run("git", "add", path)
        fixed.append(path)
        log(f"  patched {path}")

    if not fixed:
        log("Auto-fix produced no changes")
        post_slack(slack_webhook,
            [{"type": "section", "text": {"type": "mrkdwn",
              "text": f":warning: *Auto-fix could not produce a patch* for build #{run_number}.\n"
                      f"<{failed_run_url}|View logs>"}}],
            "auto-fix produced no changes")
        return 0

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

    pr_url = pr["html_url"]
    pr_number = pr["number"]
    log(f"PR #{pr_number} opened: {pr_url}")
    gh("POST", f"/repos/{repo}/issues/{pr_number}/labels",
       {"labels": ["auto-fix"]}, token=gh_token)

    post_slack(slack_webhook, [
        {"type": "header", "text": {"type": "plain_text",
          "text": f":hammer_and_wrench: Auto-fix PR opened for build #{run_number}"}},
        {"type": "section", "text": {"type": "mrkdwn",
          "text": f"Build #{run_number} on `{failed_sha[:7]}` failed compile. "
                  f"Patched {len(fixed)} file(s) and opened a PR for review."}},
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
