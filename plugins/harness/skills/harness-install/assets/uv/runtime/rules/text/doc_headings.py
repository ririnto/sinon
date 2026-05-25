#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require doc headings rule.
"""

import sys

from collections.abc import Iterable

from rules.harness_check_rule import Finding, FindingFix, FixSafety, HarnessCheckRule
from core.rule_context import RuleContext

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class DocHeadingsRule(HarnessCheckRule):
    """Validate docHeadings check."""

    category = "docHeadings"

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
        Validate docHeadings check.

        :param ctx: rule execution context.
        :returns: iterable of findings; empty when no issues are found.
        """
        section = ctx.manifest.raw.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        source_from = params.get("sourceFilesFromCategory")
        if not isinstance(source_from, str):
            return []
        source_section = ctx.manifest.raw.get(source_from, {})
        if not isinstance(source_section, dict):
            return []
        source_params = source_section.get("parameters", {})
        if not isinstance(source_params, dict):
            return []
        source_paths = source_params.get("paths", [])
        if not isinstance(source_paths, list):
            return []
        source_filter = params.get("sourceFilter", {})
        if not isinstance(source_filter, dict):
            return []
        prefix = source_filter.get("prefix", "")
        suffix = source_filter.get("suffix", "")
        filtered_files = tuple(
            p
            for p in source_paths
            if isinstance(p, str) and p.startswith(prefix) and p.endswith(suffix)
        )
        headings = params.get("headings", [])
        if not isinstance(headings, list):
            return []
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        template = messages.get("default", "doc missing {heading}: {file}")
        return [
            Finding(
                ctx.severity_of(self.category),
                self.category,
                template.format(heading=heading, file=file_path),
                file=file_path,
                start_line=1,
                start_column=1,
                end_line=1,
                end_column=1,
                fix=FindingFix(
                    description=f"add heading {heading}",
                    safety=FixSafety.MANUAL,
                ),
            )
            for file_path in filtered_files
            if ctx.is_file(file_path)
            for heading in headings
            if isinstance(heading, str) and heading not in ctx.read(file_path)
        ]


RULE: HarnessCheckRule = DocHeadingsRule()
