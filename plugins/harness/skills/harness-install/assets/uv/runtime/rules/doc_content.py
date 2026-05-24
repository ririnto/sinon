#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require doc content rule."""

from collections.abc import Iterable
from pathlib import Path
from typing import override

from harness_check_rule import Finding, HarnessCheckRule, JsonObject

from .utils import is_safe_file, read_text, severity_for


class DocContentRule(HarnessCheckRule):
    """Validate docContent check."""

    category: str = "docContent"

    @override
    def applies(self, manifest: JsonObject) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not HarnessCheckRule.is_json_object(section):
            return False
        return section.get("enabled", True) is not False

    @override
    def validate(self, project_dir: Path, manifest: JsonObject) -> Iterable[Finding]:
        """Validate docContent check."""
        section = manifest.get(self.category, {})
        if not HarnessCheckRule.is_json_object(section):
            return []
        params = section.get("parameters", {})
        if not HarnessCheckRule.is_json_object(params):
            return []
        checks = HarnessCheckRule.json_array(params.get("checks", []))
        findings: list[Finding] = []
        for check in checks:
            if not HarnessCheckRule.is_json_object(check):
                continue
            if not isinstance(check.get("files"), list):
                continue
            failure_message = check.get("failureMessage")
            if not isinstance(failure_message, str):
                continue
            if DocContentRule.condition_matches(check, DocContentRule.combined_text(project_dir, check)):
                continue
            findings.append(Finding(severity_for(manifest, self.category), self.category, failure_message))
        return findings

    @staticmethod
    def combined_text(root: Path, check: JsonObject) -> str:
        """Return newline-joined text from safe files declared by a check."""
        return "\n".join(
            read_text(root / f)
            for f in HarnessCheckRule.json_array(check.get("files", []))
            if isinstance(f, str) and is_safe_file(root / f)
        )

    @staticmethod
    def condition_matches(check: JsonObject, content: str) -> bool:
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
        if HarnessCheckRule.is_json_array(condition):
            return all(DocContentRule.evaluate_condition(item, content) for item in condition)
        if not HarnessCheckRule.is_json_object(condition):
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
        and_matches = not has_all or all(DocContentRule.evaluate_condition(item, content) for item in all_of)
        or_matches = not has_any or any(DocContentRule.evaluate_condition(item, content) for item in any_of)
        contains_matches = all(item in content for item in contains)
        not_condition = condition.get("not")
        not_matches = not has_not or not DocContentRule.evaluate_condition(not_condition, content)
        return and_matches and or_matches and contains_matches and not_matches

    @staticmethod
    def condition_array(value: object) -> list[object]:
        """Normalize a condition value into a list of condition nodes."""
        if HarnessCheckRule.is_json_array(value):
            return value
        if isinstance(value, str) or HarnessCheckRule.is_json_object(value):
            return [value]
        return []

    @staticmethod
    def string_array(value: object) -> list[str]:
        """Normalize a string or string array into string items."""
        if isinstance(value, str):
            return [value]
        if HarnessCheckRule.is_json_array(value):
            return [item for item in value if isinstance(item, str)]
        return []


RULE: HarnessCheckRule = DocContentRule()
