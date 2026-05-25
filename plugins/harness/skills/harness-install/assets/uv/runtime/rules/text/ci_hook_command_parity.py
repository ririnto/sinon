#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require CI command matches hook rule.
"""

import sys

from collections.abc import Iterable
import re

from rules.harness_check_rule import Finding, FindingFix, FixSafety, HarnessCheckRule
from core.rule_context import RuleContext, relative

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class CiHookCommandParityRule(HarnessCheckRule):
    """Validate ciHookCommandParity check."""

    category = "ciHookCommandParity"

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
        Validate ciHookCommandParity check.

        :param ctx: rule execution context.
        :returns: iterable of findings; empty when no issues are found.
        """
        result = []
        section = ctx.manifest.raw.get(self.category, {})
        if isinstance(section, dict):
            params = section.get("parameters", {})
            if isinstance(params, dict):
                ci_files = params.get("ciFiles", [])
                reference_hook = params.get("referenceHook", "")
                messages = section.get("messages", {})
                if (
                    isinstance(ci_files, list)
                    and isinstance(reference_hook, str)
                    and isinstance(messages, dict)
                ):
                    if ctx.is_file(reference_hook):
                        hook_text = ctx.read(reference_hook)
                        command_match = re.search(
                            r"# Harness validation command:\s*(.+)$",
                            hook_text,
                            re.MULTILINE,
                        )
                        command = (
                            command_match.group(1).strip() if command_match else ""
                        )
                        if command:
                            for ci_file in ci_files:
                                if isinstance(ci_file, str):
                                    if ctx.is_file(ci_file):
                                        ci_text = ctx.read(ci_file)
                                        if command not in ci_text:
                                            ci_relative = relative(ctx.path_of(ci_file), ctx.root)
                                            result.append(
                                                Finding(
                                                    ctx.severity_of(self.category),
                                                    self.category,
                                                    messages.get(
                                                        "default",
                                                        f"{ci_file}: CI command mismatch — expected {command}",
                                                    ),
                                                    file=ci_relative,
                                                    start_line=1,
                                                    start_column=1,
                                                    end_line=1,
                                                    end_column=1,
                                                    fix=FindingFix(
                                                        description=f"add line to CI that runs: {command}",
                                                        safety=FixSafety.MANUAL,
                                                        edits=(),
                                                    ),
                                                )
                                            )
        return result


RULE: HarnessCheckRule = CiHookCommandParityRule()
