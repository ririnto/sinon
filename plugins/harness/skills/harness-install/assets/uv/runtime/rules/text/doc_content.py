#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require doc content rule.
"""

import sys

from collections.abc import Iterable

from rules.harness_check_rule import Finding, FindingFix, FixSafety, HarnessCheckRule
from core.rule_context import RuleContext

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class DocContentRule(HarnessCheckRule):
    """Validate docContent check."""

    category: str = "docContent"

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
        Validate docContent check.

        :param ctx: rule execution context.
        :returns: iterable of findings; empty when no issues are found.
        """
        section = ctx.manifest.raw.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        checks = params.get("checks", [])
        if not isinstance(checks, list):
            checks = []
        findings: list[Finding] = []
        for check in checks:
            if not isinstance(check, dict):
                continue
            if not isinstance(check.get("files"), list):
                continue
            failure_message = check.get("failureMessage")
            if not isinstance(failure_message, str):
                continue
            if DocContentRule.condition_matches(
                check, DocContentRule.combined_text(ctx, check)
            ):
                continue
            files_list = check.get("files", [])
            file_str = (
                files_list[0] if files_list and isinstance(files_list[0], str) else ""
            )
            findings.append(
                Finding(
                    ctx.severity_of(self.category),
                    self.category,
                    failure_message,
                    file=file_str if file_str else None,
                    start_line=1 if file_str else None,
                    start_column=1 if file_str else None,
                    end_line=1 if file_str else None,
                    end_column=1 if file_str else None,
                    fix=FindingFix(
                        description="add required content",
                        safety=FixSafety.MANUAL,
                    ),
                )
            )
        return findings

    @staticmethod
    def combined_text(ctx: RuleContext, check: dict[str, object]) -> str:
        """Return newline-joined text from safe files declared by a check."""
        files_list = check.get("files", [])
        if not isinstance(files_list, list):
            files_list = []
        return "\n".join(
            ctx.read(f) for f in files_list if isinstance(f, str) and ctx.is_file(f)
        )

    @staticmethod
    def condition_matches(check: dict[str, object], content: str) -> bool:
        """Evaluate a nested condition expression."""
        condition = check.get("condition", check.get("when"))
        if condition is not None:
            return DocContentRule.evaluate_condition(condition, content)
        return False

    @staticmethod
    def evaluate_condition(condition: object, content: str) -> bool:
        """Evaluate nested allOf / anyOf / not / contains content conditions."""
        if isinstance(condition, str):
            return condition in content
        if isinstance(condition, list):
            return all(
                DocContentRule.evaluate_condition(item, content) for item in condition
            )
        if not isinstance(condition, dict):
            return False
        has_all = "allOf" in condition
        has_any = "anyOf" in condition
        has_contains = "contains" in condition
        has_not = "not" in condition
        if not any((has_all, has_any, has_contains, has_not)):
            return False
        all_of = DocContentRule.condition_array(condition.get("allOf"))
        any_of = DocContentRule.condition_array(condition.get("anyOf"))
        contains = DocContentRule.string_array(condition.get("contains"))
        and_matches = not has_all or all(
            DocContentRule.evaluate_condition(item, content) for item in all_of
        )
        or_matches = not has_any or any(
            DocContentRule.evaluate_condition(item, content) for item in any_of
        )
        contains_matches = all(item in content for item in contains)
        not_condition = condition.get("not")
        not_matches = not has_not or not DocContentRule.evaluate_condition(
            not_condition, content
        )
        return and_matches and or_matches and contains_matches and not_matches

    @staticmethod
    def condition_array(value: object) -> list[object]:
        """Normalize a condition value into a list of condition nodes."""
        if isinstance(value, list):
            return value
        if isinstance(value, str) or isinstance(value, dict):
            return [value]
        return []

    @staticmethod
    def string_array(value: object) -> list[str]:
        """Normalize a string or string array into string items."""
        if isinstance(value, str):
            return [value]
        if isinstance(value, list):
            return [item for item in value if isinstance(item, str)]
        return []


RULE: HarnessCheckRule = DocContentRule()
