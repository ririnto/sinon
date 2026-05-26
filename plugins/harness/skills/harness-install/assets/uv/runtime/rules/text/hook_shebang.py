#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require hook shebang rule.
"""

import sys

from collections.abc import Iterable

from rules.harness_check_rule import (
    Finding,
    FindingEdit,
    FindingFix,
    FixSafety,
    HarnessCheckRule,
)
from core.rule_context import RuleContext, relative

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class HookShebangRule(HarnessCheckRule):
    """Validate hookShebang check."""

    category = "hookShebang"

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
        Validate hookShebang check.

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
        expected_shebang = params.get("expectedShebang", "#!/usr/bin/env sh")
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        template = messages.get("default", "{hook} must start with {expectedShebang}")
        result = []
        for hook in hooks:
            if isinstance(hook, str) and ctx.first_line(hook) != expected_shebang:
                relative_path = relative(ctx.path_of(hook), ctx.root)
                result.append(
                    Finding(
                        ctx.severity_of(self.category),
                        self.category,
                        template.format(hook=hook, expectedShebang=expected_shebang),
                        file=relative_path,
                        start_line=1,
                        start_column=1,
                        end_line=1,
                        end_column=1,
                        fix=FindingFix(
                            description=f"insert `{expected_shebang}` as line 1",
                            safety=FixSafety.SAFE,
                            edits=(
                                FindingEdit(
                                    file=relative_path,
                                    start_line=1,
                                    start_column=1,
                                    end_line=1,
                                    end_column=1,
                                    replacement=f"{expected_shebang}\n",
                                ),
                            ),
                        ),
                    )
                )
        return result


RULE: HarnessCheckRule = HookShebangRule()
