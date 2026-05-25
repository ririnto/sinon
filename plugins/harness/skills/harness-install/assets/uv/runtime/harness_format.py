#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-

# /// script
# requires-python = ">=3.13"
# dependencies = ["libcst>=1.8.6"]
# ///

"""
Format harness files by applying applicable rules.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

from core.rule_context import create_rule_context
from harness_check import HarnessCheck, MANIFEST_PATH

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


def format_path(absolute_path: Path, root: Path) -> str:
    """
    Convert absolute path to relative POSIX string when contained in root.

    :param absolute_path: Absolute filesystem path.
    :param root: Root path for relative conversion.
    :returns: Relative POSIX path string when absolute_path is contained in root, otherwise the absolute path string.
    :rtype: str
    """
    return (
        absolute_path.relative_to(root).as_posix()
        if absolute_path.is_relative_to(root)
        else str(absolute_path)
    )


def main() -> None:
    """
    Read manifest, build rule context, and format applicable rules.

    :returns: None
    """
    root = Path.cwd()
    manifest = json.loads((root / MANIFEST_PATH).read_text(encoding="utf-8"))
    ctx = create_rule_context(root, manifest, stack="python")
    modified = sorted(
        [
            format_path(absolute_path, root)
            for check in HarnessCheck
            if check.rule.applies(ctx)
            for absolute_path in check.rule.format(ctx)
        ]
    )
    if modified:
        print(f"formatted: {len(modified)}")
        for path in modified:
            print(f"  {path}")
    else:
        print("no files formatted")
    sys.exit(0)


if __name__ == "__main__":
    main()
