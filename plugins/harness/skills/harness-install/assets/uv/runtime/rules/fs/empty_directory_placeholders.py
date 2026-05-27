#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require keepfile in empty directories rule.
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
from core.rule_context import RuleContext

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class EmptyDirectoryPlaceholdersRule(HarnessCheckRule):
    """Validate emptyDirectoryPlaceholders check."""

    category = "emptyDirectoryPlaceholders"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        section = ctx.manifest.raw.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate emptyDirectoryPlaceholders check."""
        section = ctx.manifest.raw.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        directories = params.get("directories", [])
        if not isinstance(directories, list):
            return []
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        findings = []
        for directory in directories:
            if not isinstance(directory, str):
                continue
            if not ctx.is_directory(directory):
                continue
            dir_path = ctx.root / directory
            if any(p for p in dir_path.iterdir() if p.name != ".gitkeep"):
                continue
            if ctx.is_file(f"{directory}/.gitkeep"):
                continue
            findings.append(
                Finding(
                    ctx.severity_of(self.category),
                    self.category,
                    messages.get(
                        "default",
                        "empty directory must keep placeholder or real files: {directory}",
                    ).format(directory=directory),
                    file=directory,
                    start_line=1,
                    start_column=1,
                    end_line=1,
                    end_column=1,
                    fix=FindingFix(
                        description="insert .gitkeep",
                        safety=FixSafety.SAFE,
                        edits=(
                            FindingEdit(
                                file=f"{directory}/.gitkeep",
                                start_line=1,
                                start_column=1,
                                end_line=1,
                                end_column=1,
                                replacement="",
                            ),
                        ),
                    ),
                )
            )
        return findings


RULE: HarnessCheckRule = EmptyDirectoryPlaceholdersRule()
