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
import os
from pathlib import Path


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
        if (
            target.is_file()
            and not target.is_symlink()
            and target.read_bytes() == source.read_bytes()
        ):
            continue
        temporary = hooks_dir / f".sync-git-hooks-{os.getpid()}-{name}"
        _ = temporary.write_bytes(source.read_bytes())
        temporary.chmod(0o755)
        _ = temporary.replace(target)


def tracked_python_files() -> list[str]:
    """
    Return Python files tracked by Git.
    """
    result = subprocess.run(
        ["git", "ls-files", "-z", "--", "*.py"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(
            result.stderr.strip() or result.stdout.strip() or "git ls-files failed"
        )
    return [path for path in result.stdout.split("\0") if path]


def main() -> int:
    """
    Synchronize Git hooks, then run ruff lint and format checks on the project.
    """
    sync_git_hooks()
    try:
        files = tracked_python_files()
    except RuntimeError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    if not files:
        print("ruff: no tracked Python files to check")
        return 0
    commands = (
        ("check",),
        ("format", "--check"),
    )
    status = 0
    for command in commands:
        result = subprocess.run(
            [
                "uvx",
                "--with",
                "ruff>=0.15.16,<0.16.0",
                "ruff",
                *command,
                "--",
                *files,
            ],
        )
        if result.returncode != 0:
            status = result.returncode
    return status


if __name__ == "__main__":
    raise SystemExit(main())
