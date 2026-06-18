#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///

"""Create a Claude Code worktree for the current repository."""

from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess
import sys


def safe_name(raw_name: str | None, fallback: str) -> str:
    """Return a filesystem-safe name."""

    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "-", raw_name or fallback)
    return cleaned.strip(".-_") or fallback


def default_branch(repo: Path) -> str:
    """Return the default branch reported by origin."""

    remote = subprocess.check_output(
        ["git", "remote", "show", "origin"],
        cwd=repo,
        text=True,
    )
    for line in remote.splitlines():
        if "HEAD branch:" in line:
            return line.split(":", 1)[1].strip()
    return "main"


def main() -> int:
    """Create the worktree and print its path."""

    payload = json.load(sys.stdin)
    raw_name = payload.get("name") if isinstance(payload, dict) else None
    name = safe_name(raw_name if isinstance(raw_name, str) else None, "worktree")
    repo_text = subprocess.check_output(
        ["git", "rev-parse", "--show-toplevel"],
        cwd=Path.cwd(),
        text=True,
    )
    repo = Path(repo_text.strip())
    repo_slug = safe_name(repo.name, "repo")
    target = Path.home() / ".claude" / "worktrees" / repo_slug / name
    target.parent.mkdir(parents=True, exist_ok=True)
    base = default_branch(repo)
    subprocess.run(
        ["git", "fetch", "origin"],
        cwd=repo,
        check=True,
        stdout=sys.stderr,
        stderr=sys.stderr,
    )
    subprocess.run(
        ["git", "worktree", "add", "-b", f"claude/{name}", str(target), f"origin/{base}"],
        cwd=repo,
        check=True,
        stdout=sys.stderr,
        stderr=sys.stderr,
    )
    print(target)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
