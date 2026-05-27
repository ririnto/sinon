#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require hook stage rule.
"""

import sys

from collections.abc import Iterable

from rules.harness_check_rule import Finding, FindingFix, FixSafety, HarnessCheckRule
from core.rule_context import RuleContext, relative

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class HookStageRule(HarnessCheckRule):
    """Validate hookStage check."""

    category = "hookStage"

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
        Validate hookStage check.

        :param ctx: rule execution context.
        :returns: iterable of findings; empty when no issues are found.
        """
        section = ctx.manifest.raw.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        marker_template = params.get("markerTemplate", "# Harness stage: {stage}")
        stages = params.get("stages", {})
        if not isinstance(stages, dict):
            return []
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        template = messages.get(
            "default",
            "{hook} must contain stage marker '# Harness stage: {expectedStage}'",
        )
        result = []
        for hook_name, expected_stage in stages.items():
            if isinstance(hook_name, str) and isinstance(expected_stage, str):
                hook_path = f"docs/harness/git-hooks/{hook_name}"
                if ctx.is_file(hook_path) and marker_template.format(
                    stage=expected_stage
                ) not in ctx.read(hook_path):
                    relative_path = relative(ctx.path_of(hook_path), ctx.root)
                    result.append(
                        Finding(
                            ctx.severity_of(self.category),
                            self.category,
                            template.format(
                                hook=hook_name, expectedStage=expected_stage
                            ),
                            file=relative_path,
                            start_line=1,
                            start_column=1,
                            end_line=1,
                            end_column=1,
                            fix=FindingFix(
                                description=f"add stage marker '# Harness stage: {expected_stage}' to hook",
                                safety=FixSafety.MANUAL,
                                edits=(),
                            ),
                        )
                    )
        return result


RULE: HarnessCheckRule = HookStageRule()
