#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require hook generated marker rule.
"""

import sys

from collections.abc import Iterable
from pathlib import Path

from rules.harness_check_rule import Finding, FindingEdit, FindingFix, FixSafety, HarnessCheckRule
from core.rule_context import RuleContext, relative

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class HookGeneratedMarkerRule(HarnessCheckRule):
    """Validate hookGeneratedMarker check."""

    category = "hookGeneratedMarker"

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
        Validate hookGeneratedMarker check.

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
        marker_template = params.get(
            "markerTemplate", "# Harness generated hook: {name}"
        )
        placeholder_forbidden = params.get(
            "placeholderForbidden",
            "packaged placeholder is replaced during harness installation",
        )
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        valid_hooks = [
            (hook, ctx.read(hook), Path(hook).name)
            for hook in hooks
            if isinstance(hook, str) and ctx.is_file(hook)
        ]
        findings = []
        for hook, text, hook_name in valid_hooks:
            if marker_template.format(name=hook_name) not in text:
                relative_path = relative(ctx.path_of(hook), ctx.root)
                marker_line = marker_template.format(name=hook_name)
                findings.append(
                    Finding(
                        ctx.severity_of(self.category),
                        self.category,
                        messages.get(
                            "missingMarker", "{hook} must contain generated marker '{marker}'"
                        ).format(hook=hook, marker=marker_line),
                        file=relative_path,
                        start_line=1,
                        start_column=1,
                        end_line=1,
                        end_column=1,
                        fix=FindingFix(
                            description=f"insert `{marker_line}` as line 1",
                            safety=FixSafety.SAFE,
                            edits=(
                                FindingEdit(
                                    file=relative_path,
                                    start_line=1,
                                    start_column=1,
                                    end_line=1,
                                    end_column=1,
                                    replacement=f"{marker_line}\n",
                                ),
                            ),
                        ),
                    )
                )
        for hook, text, _ in valid_hooks:
            if isinstance(placeholder_forbidden, str) and placeholder_forbidden in text:
                relative_path = relative(ctx.path_of(hook), ctx.root)
                findings.append(
                    Finding(
                        ctx.severity_of(self.category),
                        self.category,
                        messages.get(
                            "placeholderPresent",
                            "{hook} still contains packaging placeholder text",
                        ).format(hook=hook),
                        file=relative_path,
                        start_line=1,
                        start_column=1,
                        end_line=1,
                        end_column=1,
                        fix=FindingFix(
                            description="remove placeholder text from hook file",
                            safety=FixSafety.MANUAL,
                            edits=(),
                        ),
                    )
                )
        return findings


RULE: HarnessCheckRule = HookGeneratedMarkerRule()
