#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
# /// script
# requires-python = ">=3.13"
# dependencies = []
# ///

"""
Check runner using native ruff.
"""

from __future__ import annotations

import subprocess
import shutil
import sys


def run_markdownlint() -> int:
    """
    Run markdownlint-cli2 when it is available on PATH.
    """
    markdownlint = shutil.which("markdownlint-cli2")
    if markdownlint is None:
        print(
            "warning: markdownlint-cli2 not in PATH; skipping Markdown linting",
            file=sys.stderr,
        )
        return 0
    return subprocess.run([markdownlint]).returncode


def main() -> int:
    """
    Validate Markdown and run ruff lint and format checks on the project.
    """
    markdown_status = run_markdownlint()
    if markdown_status != 0:
        return markdown_status
    commands = (
        ("check", "."),
        ("format", "--check", "."),
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
            ],
        )
        if result.returncode != 0:
            status = result.returncode
    return status


if __name__ == "__main__":
    raise SystemExit(main())
