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


def validate_enter_worktree_hooks(
    settings_path: Path, settings: dict[str, JsonValue]
) -> list[str]:
    """Return EnterWorktree hook contract violations."""
    errors: list[str] = []
    hooks = settings.get("hooks")
    if not isinstance(hooks, dict):
        return [f"{settings_path}: missing hooks object"]
    post_tool_use = hooks.get("PostToolUse")
    if not isinstance(post_tool_use, list) or not post_tool_use:
        return [f"{settings_path}: missing hooks.PostToolUse[]"]
    if len(post_tool_use) != 1:
        errors.append(f"{settings_path}: hooks.PostToolUse must define one matcher group")
    matcher_group = post_tool_use[0]
    if not isinstance(matcher_group, dict):
        return [*errors, f"{settings_path}: hooks.PostToolUse[0] must be an object"]
    if matcher_group.get("matcher") != "EnterWorktree":
        errors.append(
            f"{settings_path}: hooks.PostToolUse[0] must match EnterWorktree"
        )
    handlers = matcher_group.get("hooks")
    if not isinstance(handlers, list) or not handlers:
        return [*errors, f"{settings_path}: missing hooks.PostToolUse[0].hooks[]"]
    for index, handler in enumerate(handlers):
        prefix = f"{settings_path}: hooks.PostToolUse[0].hooks[{index}]"
        if not isinstance(handler, dict):
            errors.append(f"{prefix} must be an object")
            continue
        if handler.get("type") != "command":
            errors.append(f"{prefix} must use type='command'")
        if handler.get("async") is not True:
            errors.append(f"{prefix} must set async=true")
        if "args" in handler:
            errors.append(f"{prefix} must not define args")
        if "matcher" in handler:
            errors.append(f"{prefix} must not define matcher")
        if not isinstance(handler.get("command"), str) or handler.get("command") == "":
            errors.append(f"{prefix} must define a non-empty command")
    return errors


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
    errors.extend(validate_enter_worktree_hooks(settings_path, settings))
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
