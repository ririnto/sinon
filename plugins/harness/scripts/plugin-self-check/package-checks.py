#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Validate Sinon plugin package metadata and inventories."""

from __future__ import annotations

import sys
from pathlib import Path

from package_checks_common import configure_utf8_output
from package_checks_inventory import validate


def main(argv: list[str]) -> int:
    """Run plugin package validation from the repository root."""
    configure_utf8_output()
    root = Path(argv[1] if len(argv) > 1 else ".").resolve()
    errors = validate(root)
    if errors:
        for error in errors:
            print(f"error: {error}", file=sys.stderr)
        return 1
    print("Plugin package validation passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
