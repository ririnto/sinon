#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Forbid unchecked tasks under rule.
"""

import sys

from collections.abc import Iterable
import fnmatch
import re

from rules.harness_check_rule import Finding, FindingFix, FixSafety, HarnessCheckRule
from core.rule_context import RuleContext, relative

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class UncheckedTasksRule(HarnessCheckRule):
    """Validate uncheckedTasks check."""

    category = "uncheckedTasks"

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
        Validate uncheckedTasks check.

        :param ctx: rule execution context.
        :returns: iterable of findings; empty when no issues are found.
        """
        section = ctx.manifest.raw.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        directory = params.get("directory", "docs/exec-plans/completed")
        filename_pattern_str = params.get("filenamePattern", "*.md")
        unchecked_pattern_str = params.get("uncheckedTaskPattern", r"^\s*-\s*\[ \]\s")
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        template = messages.get("default", "completed plan has unchecked tasks: {file}")
        if not isinstance(directory, str):
            return []
        if not ctx.is_directory(directory):
            return []
        unchecked_pattern = re.compile(unchecked_pattern_str)
        pattern_glob = (
            filename_pattern_str if isinstance(filename_pattern_str, str) else "*.md"
        )
        dir_path = ctx.root / directory
        findings = []
        for path in sorted(dir_path.iterdir()):
            if not path.is_file():
                continue
            if path.name == ".gitkeep":
                continue
            if not fnmatch.fnmatch(path.name, pattern_glob):
                continue
            text = ctx.read(path.relative_to(ctx.root).as_posix())
            lines = text.splitlines()
            for line_num, line in enumerate(lines, 1):
                if unchecked_pattern.search(line):
                    rel_path = relative(path, ctx.root)
                    findings.append(
                        Finding(
                            ctx.severity_of(self.category),
                            self.category,
                            template.format(file=rel_path),
                            file=rel_path,
                            start_line=line_num,
                            start_column=1,
                            end_line=line_num,
                            end_column=len(line) + 1,
                            fix=FindingFix(
                                description="check off task",
                                safety=FixSafety.MANUAL,
                            ),
                        )
                    )
        return findings


RULE: HarnessCheckRule = UncheckedTasksRule()
