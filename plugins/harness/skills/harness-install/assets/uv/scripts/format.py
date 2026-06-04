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

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


def main() -> int:
    """
    Run ruff format on the project.
    """
    command = [
        "uvx",
        "--with",
        "ruff>=0.15.15,<0.16.0",
        "ruff",
        "format",
        ".",
    ]
    result = subprocess.run(command)
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
