#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
# /// script
# requires-python = ">=3.13"
# dependencies = []
# ///
"""
Fix runner using native ruff.
"""

from __future__ import annotations

import shutil
import subprocess
import sys


def run_markdownlint_fix() -> int:
    """
    Run markdownlint-cli2 --fix when it is available on PATH.
    """
    markdownlint = shutil.which("markdownlint-cli2")
    if markdownlint is None:
        print(
            "warning: markdownlint-cli2 not in PATH; skipping Markdown fixes",
            file=sys.stderr,
        )
        return 0
    return subprocess.run([markdownlint, "--fix"]).returncode


def main() -> int:
    """
    Run Markdown fixes, then ruff lint fixes and format fixes on the project.
    """
    markdown_status = run_markdownlint_fix()
    if markdown_status != 0:
        return markdown_status
    commands = (
        ("check", "--fix", "."),
        ("format", "."),
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
