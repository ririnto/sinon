#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require files exist rule.
"""

import sys


from collections.abc import Iterable

from rules.harness_check_rule import (
    Finding,
    FindingFix,
    FixSafety,
    HarnessCheckRule,
)
from core.rule_context import RuleContext

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class FilePresenceRule(HarnessCheckRule):
    """Validate filePresence check."""

    category = "filePresence"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        section = ctx.manifest.raw.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate filePresence check."""
        section = ctx.manifest.raw.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        paths = params.get("paths", [])
        if not isinstance(paths, list):
            return []
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        return [
            Finding(
                ctx.severity_of(self.category),
                self.category,
                messages.get("default", "missing file: {path}").format(path=path),
                file=path,
                start_line=1,
                start_column=1,
                end_line=1,
                end_column=1,
                fix=FindingFix(
                    description=f"create {path}",
                    safety=FixSafety.MANUAL,
                ),
            )
            for path in paths
            if isinstance(path, str) and not ctx.is_file(path)
        ]


RULE: HarnessCheckRule = FilePresenceRule()
