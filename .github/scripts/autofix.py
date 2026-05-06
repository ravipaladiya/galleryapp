#!/usr/bin/env python3
"""
Auto-fix Kotlin compile errors using Claude.

Triggered from .github/workflows/build-and-notify.yml when the build job fails.
Reads /tmp/build.log (uploaded as workflow artifact), extracts unique error files,
sends each to Claude with a tightly-scoped prompt, applies the returned content,
opens a PR with the patch, and pings Slack.

Required env:
    ANTHROPIC_API_KEY   sk-ant-oat... (OAuth) or sk-ant-api... (regular)
    GITHUB_TOKEN        repo PAT with contents:write + pull-requests:write
    REPO                owner/repo
    RUN_NUMBER          numeric build run number
    FAILED_SHA          commit sha that failed
    FAILED_RUN_URL      url back to the failing run
    SLACK_WEBHOOK_URL   (optional) for notification
"""
import json
import os
import re
import subprocess
import sys
import urllib.request
import urllib.error
from pathlib import Path

LOG_PATH = "/tmp/build.log"
MODEL = "claude-sonnet-4-6"
MAX_TOKENS = 8000


def env(name, required=True):
    v = os.environ.get(name, "")
    if required and not v:
        sys.exit(f"missing env var: {name}")
    return v


def log(msg):
    print(f"[autofix] {msg}", flush=True)


def run(*args, check=True, capture=False):
    log(f"$ {' '.join(args)}")
    return subprocess.run(args, check=check, text=True,
                          capture_output=capture)


def parse_kotlin_errors(log_text):
    """
    Extract unique source files referenced by Kotlin compile errors.
    Lines look like:
        e: file:///home/runner/work/galleryapp/galleryapp/app/src/main/.../X.kt:402:56 Unresolved reference 'clickable'.
    """
    pattern = re.compile(
        r"^e: file://(?P<path>/[^:]+\.kt):(?P<line>\d+):(?P<col>\d+)\s+(?P<msg>.+)$",
        re.MULTILINE,
    )
    errors_by_file = {}
    for m in pattern.finditer(log_text):
        # strip the runner workspace prefix to get a repo-relative path
        full = m.group("path")
        idx = full.find("/galleryapp/galleryapp/")
        rel = full[idx + len("/galleryapp/galleryapp/"):] if idx != -1 else full
        errors_by_file.setdefault(rel, []).append({
            "line": int(m.group("line")),
            "col": int(m.group("col")),
            "msg": m.group("msg").strip(),
        })
    return errors_by_file


def call_claude(api_key, prompt):
    is_oauth = api_key.startswith("sk-ant-oat")
    headers = {
        "anthropic-version": "2023-06-01",
        "content-type": "application/json",
    }
    if is_oauth:
        headers["Authorization"] = f"Bearer {api_key}"
        headers["anthropic-beta"] = "oauth-2025-04-20"
    else:
        headers["x-api-key"] = api_key

    body = json.dumps({
        "model": MODEL,
        "max_tokens": MAX_TOKENS,
        "system": (
            "You are an expert Kotlin / Android Jetpack Compose engineer fixing "
            "compile errors in a single source file. Make the MINIMUM change "
            "needed to fix the listed compile errors. Do not refactor. Do not "
            "rename. Do not change logic. Add missing imports if needed. "
            "Return ONLY the complete corrected file contents — no markdown "
            "fences, no explanation, no commentary. The output must be valid "
            "Kotlin and must be a drop-in replacement for the original file."
        ),
        "messages": [{"role": "user", "content": prompt}],
    }).encode()

    req = urllib.request.Request(
        "https://api.anthropic.com/v1/messages",
        data=body, method="POST", headers=headers,
    )
    try:
        resp = urllib.request.urlopen(req, timeout=120)
        data = json.loads(resp.read())
    except urllib.error.HTTPError as e:
        sys.exit(f"Anthropic API error {e.code}: {e.read().decode()}")
    text_blocks = [b["text"] for b in data["content"] if b.get("type") == "text"]
    return "".join(text_blocks).strip()


def post_slack(webhook, blocks, fallback):
    if not webhook:
        return
    payload = {"text": fallback, "blocks": blocks}
    try:
        urllib.request.urlopen(urllib.request.Request(
            webhook, data=json.dumps(payload).encode(),
            headers={"Content-Type": "application/json"},
        ), timeout=15)
    except Exception as e:
        log(f"slack post failed: {e}")


def gh(method, path, data=None, token=None):
    url = f"https://api.github.com{path}"
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(url, data=body, method=method, headers={
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "Content-Type": "application/json",
        "User-Agent": "autofix",
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
        log(f"{LOG_PATH} not found — nothing to fix")
        return 0

    log_text = Path(LOG_PATH).read_text(errors="replace")
    errors_by_file = parse_kotlin_errors(log_text)
    if not errors_by_file:
        log("No Kotlin compile errors found in build log — skipping auto-fix")
        return 0

    log(f"Found errors in {len(errors_by_file)} file(s): {list(errors_by_file.keys())}")

    # Configure git
    run("git", "config", "user.name",  "galleryapp-autofix[bot]")
    run("git", "config", "user.email", "autofix@noreply.github.com")

    branch = f"auto-fix/build-{run_number}"
    # If branch already exists, fall back to a unique-by-sha name
    res = run("git", "ls-remote", "--exit-code", "--heads", "origin", branch,
              check=False, capture=True)
    if res.returncode == 0:
        branch = f"auto-fix/build-{run_number}-{failed_sha[:7]}"

    run("git", "checkout", "-b", branch)

    fixed = []
    skipped = []
    for path, errs in errors_by_file.items():
        p = Path(path)
        if not p.exists():
            log(f"  SKIP {path}: not found")
            skipped.append(path)
            continue
        original = p.read_text()
        err_block = "\n".join(f"  line {e['line']}:{e['col']}: {e['msg']}" for e in errs)
        prompt = (
            f"File: {path}\n\n"
            f"Compile errors in this file:\n{err_block}\n\n"
            f"Current contents:\n```kotlin\n{original}\n```\n\n"
            f"Return only the corrected file contents."
        )
        log(f"  Fixing {path} ({len(errs)} error(s))...")
        fixed_content = call_claude(api_key, prompt)
        # Strip stray markdown fences in case the model ignored instructions
        if fixed_content.startswith("```"):
            fixed_content = re.sub(r"^```[a-zA-Z]*\n", "", fixed_content)
            fixed_content = re.sub(r"\n```\s*$", "", fixed_content)
        if fixed_content == original:
            log(f"  No change for {path}")
            skipped.append(path)
            continue
        if not fixed_content.strip():
            log(f"  Empty response for {path} — skipping")
            skipped.append(path)
            continue
        p.write_text(fixed_content)
        run("git", "add", path)
        fixed.append(path)

    if not fixed:
        log("Auto-fix produced no changes")
        post_slack(slack_webhook,
            [{"type": "section", "text": {"type": "mrkdwn",
              "text": f":warning: *Auto-fix could not produce a patch* for build #{run_number}.\n"
                      f"<{failed_run_url}|View logs> · commit `{failed_sha[:7]}`"}}],
            "auto-fix produced no changes")
        return 0

    msg_files = "\n  - " + "\n  - ".join(fixed)
    err_summary = []
    for f in fixed:
        for e in errors_by_file[f]:
            err_summary.append(f"  {f}:{e['line']} — {e['msg']}")
    err_block = "\n".join(err_summary)

    commit_msg = (
        f"auto-fix: resolve compile errors from build #{run_number}\n\n"
        f"Failing commit: {failed_sha[:7]}\nFailing run: {failed_run_url}\n\n"
        f"Errors:\n{err_block}"
    )
    run("git", "commit", "-m", commit_msg)
    run("git", "push", "origin", branch)

    pr_body = (
        f"Auto-generated by `.github/workflows/build-and-notify.yml` after "
        f"build #{run_number} failed on `{failed_sha[:7]}`.\n\n"
        f"**Failing run:** {failed_run_url}\n\n"
        f"**Files patched:**\n```\n{msg_files.strip()}\n```\n\n"
        f"**Errors that triggered this:**\n```\n{err_block}\n```\n\n"
        f"> :warning: Review carefully before merging — patch was generated by an LLM."
    )
    code, pr = gh("POST", f"/repos/{repo}/pulls", {
        "title": f"auto-fix: resolve build #{run_number} compile errors",
        "head": branch,
        "base": "main",
        "body": pr_body,
        "maintainer_can_modify": True,
    }, token=gh_token)
    if code not in (200, 201):
        log(f"PR creation failed {code}: {pr}")
        post_slack(slack_webhook,
            [{"type": "section", "text": {"type": "mrkdwn",
              "text": f":x: *Auto-fix branch pushed but PR creation failed* for build #{run_number}.\n"
                      f"branch: `{branch}` · <{failed_run_url}|build logs>\nerror: `{pr}`"}}],
            "auto-fix PR creation failed")
        return 1

    pr_url = pr["html_url"]
    pr_number = pr["number"]
    log(f"PR #{pr_number} opened: {pr_url}")

    # Best-effort label
    gh("POST", f"/repos/{repo}/issues/{pr_number}/labels",
       {"labels": ["auto-fix"]}, token=gh_token)

    post_slack(slack_webhook,
        [
            {"type": "header", "text": {"type": "plain_text",
              "text": f":hammer_and_wrench: Auto-fix PR opened for build #{run_number}"}},
            {"type": "section", "text": {"type": "mrkdwn",
              "text": f"Build #{run_number} on `{failed_sha[:7]}` failed Kotlin compile. "
                      f"I patched `{', '.join(fixed)}` and opened a PR — please review."}},
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
        ],
        f"Auto-fix PR #{pr_number} opened for build #{run_number}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
