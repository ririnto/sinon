#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-

# /// script
# requires-python = ">=3.13"
# dependencies = []
# ///
"""
Harness format runner using native ruff.
"""

from __future__ import annotations

import subprocess
import sys


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
        message = (
            result.stderr.strip() or result.stdout.strip() or "git ls-files failed"
        )
        raise RuntimeError(message)
    return [path for path in result.stdout.split("\0") if path]


def main() -> int:
    """
    Run ruff format on the project.
    """
    try:
        files = tracked_python_files()
    except RuntimeError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    if not files:
        print("ruff: no tracked Python files to format")
        return 0
    command = [
        "uvx",
        "--with",
        "ruff>=0.15.15,<0.16.0",
        "ruff",
        "format",
        "--",
        *files,
    ]
    result = subprocess.run(command)
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
