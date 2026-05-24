#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require hook shebang rule."""

from collections.abc import Iterable

from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from .utils import first_line, severity_for

class HookShebangRule(HarnessCheckRule):
    """Validate hookShebang check."""

    category = "hookShebang"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate hookShebang check."""
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        hooks = params.get("hooks", [])
        if not isinstance(hooks, list):
            return []
        expected_shebang = params.get("expectedShebang", "#!/usr/bin/env sh")
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        template = messages.get("default", "{hook} must start with {expectedShebang}")
        return [
            Finding(
                severity_for(manifest, self.category),
                self.category,
                template.format(hook=hook, expectedShebang=expected_shebang),
            )
            for hook in hooks
            if isinstance(hook, str) and first_line(root / hook) != expected_shebang
        ]


RULE: HarnessCheckRule = HookShebangRule()
