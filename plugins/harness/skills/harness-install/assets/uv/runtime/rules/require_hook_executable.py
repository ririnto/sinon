#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require hook executable rule."""

from collections.abc import Iterable

from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import is_executable, severity_for

class RequireHookExecutableRule(HarnessCheckRule):
    """Validate requireHookExecutable check."""

    category = "requireHookExecutable"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate requireHookExecutable check."""
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        hooks = params.get("hooks", [])
        if not isinstance(hooks, list):
            return []
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        template = messages.get("default", "{hook} must be executable")
        return [
            Finding(
                severity_for(manifest, self.category),
                self.category,
                template.format(hook=hook),
            )
            for hook in hooks
            if isinstance(hook, str) and not is_executable(root / hook)
        ]


RULE: HarnessCheckRule = RequireHookExecutableRule()
