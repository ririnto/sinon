#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require single top level kotlin declaration rule."""

import sys

import re
from collections.abc import Iterable

from rules.harness_check_rule import (
    Finding,
    FindingFix,
    FixSafety,
    HarnessCheckRule,
)
from core.rule_context import RuleContext, relative

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class KotlinTopLevelDeclarationCountRule(HarnessCheckRule):
    """Validate kotlinTopLevelDeclarationCount check."""

    category = "kotlinTopLevelDeclarationCount"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        if not ctx.is_enabled(self.category):
            return False
        section = ctx.manifest.raw.get(self.category)
        if not isinstance(section, dict):
            return False
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return False
        directories = params.get("directories", [])
        if not isinstance(directories, list):
            return False
        return len(directories) > 0

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate kotlinTopLevelDeclarationCount check."""
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
        template = messages.get(
            "default",
            "kotlin file must have exactly one top-level declaration: {file}",
        )
        severity = ctx.severity_of(self.category)
        top_level_pattern = re.compile(
            r"^(class|interface|enum class|data class|sealed class|object|abstract class|val|var|fun|typealias)\b",
            re.MULTILINE,
        )
        def collect_findings():
            for directory in directories:
                if not isinstance(directory, str):
                    continue
                ctx.root / directory
                if not ctx.is_directory(directory):
                    continue
                for file_path in ctx.walk_directory(directory)[0]:
                    if file_path.suffix != ".kt":
                        continue
                    text = ctx.read(file_path.relative_to(ctx.root).as_posix())
                    matches = top_level_pattern.findall(text)
                    if len(matches) != 1:
                        rel_path = relative(file_path, ctx.root)
                        lines = text.splitlines()
                        first_match_line = 1
                        for i, line in enumerate(lines, 1):
                            if top_level_pattern.search(line):
                                first_match_line = i
                                break
                        yield Finding(
                            severity,
                            self.category,
                            template.format(file=rel_path),
                            file=rel_path,
                            start_line=first_match_line,
                            start_column=1,
                            end_line=first_match_line,
                            end_column=len(lines[first_match_line - 1]) + 1 if first_match_line <= len(lines) else 1,
                            fix=FindingFix(
                                description="split file to have exactly one top-level declaration",
                                safety=FixSafety.MANUAL,
                                edits=(),
                            ),
                        )
        return list(collect_findings())


RULE: HarnessCheckRule = KotlinTopLevelDeclarationCountRule()
