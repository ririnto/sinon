#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require hook executable rule.
"""

import sys

from collections.abc import Iterable

from rules.harness_check_rule import Finding, FindingFix, FixSafety, HarnessCheckRule
from core.rule_context import RuleContext, relative

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class HookExecutableRule(HarnessCheckRule):
    """Validate hookExecutable check."""

    category = "hookExecutable"

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
        Validate hookExecutable check.

        :param ctx: rule execution context.
        :returns: iterable of findings; empty when no issues are found.
        """
        section = ctx.manifest.raw.get(self.category, {})
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
        result = []
        for hook in hooks:
            if isinstance(hook, str) and not ctx.is_executable(hook):
                result.append(
                    Finding(
                        ctx.severity_of(self.category),
                        self.category,
                        messages.get("default", "{hook} must be executable").format(hook=hook),
                        file=relative(ctx.path_of(hook), ctx.root),
                        start_line=1,
                        start_column=1,
                        end_line=1,
                        end_column=1,
                        fix=FindingFix(
                            description="make file executable (chmod +x)",
                            safety=FixSafety.SAFE,
                            edits=(),
                        ),
                    )
                )
        return result


RULE: HarnessCheckRule = HookExecutableRule()
