#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-

# /// script
# requires-python = ">=3.13"
# dependencies = []
# ///
"""
Harness check runner using native ruff with Git hook synchronization.
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


def sync_git_hooks() -> None:
    """
    Synchronize tracked Git hook templates into the active hooks directory.
    """
    result = subprocess.run(
        ["git", "rev-parse", "--git-path", "hooks"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return
    hooks_dir = Path(result.stdout.strip())
    hooks_dir.mkdir(parents=True, exist_ok=True)
    for name in ("pre-commit", "pre-push"):
        source = Path("docs/harness/git-hooks") / name
        if not source.is_file():
            continue
        target = hooks_dir / name
        if target.is_file() and target.read_bytes() == source.read_bytes():
            continue
        target.write_bytes(source.read_bytes())
        target.chmod(0o755)


def main() -> int:
    """
    Synchronize Git hooks, then run ruff check on the project.
    """
    sync_git_hooks()
    command = [
        "uvx",
        "--with",
        "ruff>=0.15.15,<0.16.0",
        "ruff",
        "check",
        ".",
    ]
    result = subprocess.run(command)
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
