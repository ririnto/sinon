#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Validate installed Claude settings assets."""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Final

from package_checks_common import JsonValue, Reporter, configure_utf8_output, read_json


REQUIRED_SETTINGS: Final[dict[str, JsonValue]] = {
    "$schema": "https://json.schemastore.org/claude-code-settings.json",
    "includeCoAuthoredBy": False,
    "includeGitInstructions": False,
    "showClearContextOnPlanAccept": True,
}


def validate_settings(settings_path: Path) -> list[str]:
    """Return durable Claude settings contract violations."""
    reporter = Reporter(settings_path.parent)
    settings = read_json(settings_path, reporter)
    if reporter.errors:
        return reporter.errors
    errors: list[str] = []
    for key, expected in REQUIRED_SETTINGS.items():
        if settings.get(key) != expected:
            errors.append(f"{settings_path}: expected {key}={expected!r}")
    env = settings.get("env")
    if (
        not isinstance(env, dict)
        or env.get("CLAUDE_BASH_MAINTAIN_PROJECT_WORKING_DIR") != "1"
    ):
        errors.append(
            f"{settings_path}: missing CLAUDE_BASH_MAINTAIN_PROJECT_WORKING_DIR"
        )
    hooks = settings.get("hooks")
    if not isinstance(hooks, dict) or "WorktreeCreate" not in hooks:
        errors.append(f"{settings_path}: missing WorktreeCreate hook")
    return errors


def main(argv: list[str]) -> int:
    """Run Claude settings asset validation."""
    configure_utf8_output()
    if len(argv) != 1:
        print("usage: asset_checks_settings.py <settings.json>", file=sys.stderr)
        return 2
    errors = validate_settings(Path(argv[0]))
    for error in errors:
        print(error, file=sys.stderr)
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
