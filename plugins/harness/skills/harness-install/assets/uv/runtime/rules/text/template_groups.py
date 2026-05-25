#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require template groups rule.
"""

import sys

from collections.abc import Iterable

from rules.harness_check_rule import Finding, FindingFix, FixSafety, HarnessCheckRule
from core.rule_context import RuleContext

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class TemplateGroupsRule(HarnessCheckRule):
    """Validate templateGroups check."""

    category = "templateGroups"

    def applies(self, ctx: RuleContext) -> bool:
        """
        Check if this rule applies to the context.

        :param ctx: rule execution context.
        :returns: ``True`` when the rule should run.
        """
        section = ctx.manifest.raw.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """
        Validate templateGroups check.

        :param ctx: rule execution context.
        :returns: iterable of findings; empty when no issues are found.
        """
        section = ctx.manifest.raw.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        target_root = params.get("targetRoot", "docs/harness/templates")
        groups = params.get("groups", [])
        if not isinstance(groups, list):
            return []
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        template = messages.get(
            "default", "missing template group: {targetRoot}/{group}"
        )
        return [
            Finding(
                ctx.severity_of(self.category),
                self.category,
                template.format(targetRoot=target_root, group=group),
                file=f"{target_root}/{group}",
                start_line=1,
                start_column=1,
                end_line=1,
                end_column=1,
                fix=FindingFix(
                    description=f"create template group {group}",
                    safety=FixSafety.MANUAL,
                ),
            )
            for group in groups
            if isinstance(group, str) and not ctx.is_directory(f"{target_root}/{group}")
        ]


RULE: HarnessCheckRule = TemplateGroupsRule()
