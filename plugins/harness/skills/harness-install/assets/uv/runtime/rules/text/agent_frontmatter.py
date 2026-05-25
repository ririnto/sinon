#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require agent frontmatter rule.
"""

import sys

from collections.abc import Iterable
import re

from rules.harness_check_rule import Finding, FindingFix, FixSafety, HarnessCheckRule
from core.rule_context import RuleContext, relative

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class AgentFrontmatterRule(HarnessCheckRule):
    """Validate agentFrontmatter check."""

    category = "agentFrontmatter"

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
        Validate agentFrontmatter check.

        :param ctx: rule execution context.
        :returns: iterable of findings; empty when no issues are found.
        """
        section = ctx.manifest.raw.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        directory = params.get("directory", ".claude/agents")
        required_fields = params.get("requiredFields", [])
        if not isinstance(required_fields, list):
            return []
        name_pattern_str = params.get("namePattern", "(?m)^name:\\s*[-a-z0-9]+\\s*$")
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        name_pattern = re.compile(name_pattern_str)
        missing_dir_msg = messages.get(
            "missingDirectory", ".claude/agents must contain at least one .md agent"
        )
        if not ctx.is_directory(directory):
            return [
                Finding(
                    ctx.severity_of(self.category),
                    self.category,
                    missing_dir_msg,
                    file=directory,
                    start_line=1,
                    start_column=1,
                    end_line=1,
                    end_column=1,
                    fix=FindingFix(
                        description="create agents directory",
                        safety=FixSafety.MANUAL,
                    ),
                )
            ]
        dir_path = ctx.root / directory
        files = tuple(
            sorted(
                p
                for p in ctx.walk_directory(directory)[0]
                if p.parent == dir_path and p.suffix == ".md"
            )
        )
        if not files:
            return [
                Finding(
                    ctx.severity_of(self.category),
                    self.category,
                    missing_dir_msg,
                    file=directory,
                    start_line=1,
                    start_column=1,
                    end_line=1,
                    end_column=1,
                    fix=FindingFix(
                        description="create at least one agent file",
                        safety=FixSafety.MANUAL,
                    ),
                )
            ]
        findings = []
        for path in files:
            text = ctx.read(path.relative_to(ctx.root).as_posix())
            severity = ctx.severity_of(self.category)
            rel_path = relative(path, ctx.root)
            if not text.startswith("---"):
                findings.append(
                    Finding(
                        severity,
                        self.category,
                        messages.get(
                            "missingFrontmatter", "agent missing frontmatter: {file}"
                        ).format(file=rel_path),
                        file=rel_path,
                        start_line=1,
                        start_column=1,
                        end_line=1,
                        end_column=1,
                        fix=FindingFix(
                            description="add frontmatter block",
                            safety=FixSafety.MANUAL,
                        ),
                    )
                )
            if text.startswith("---") and not name_pattern.search(text):
                findings.append(
                    Finding(
                        severity,
                        self.category,
                        messages.get(
                            "missingName", "agent missing name: {file}"
                        ).format(file=rel_path),
                        file=rel_path,
                        start_line=1,
                        start_column=1,
                        end_line=1,
                        end_column=1,
                        fix=FindingFix(
                            description="add name field",
                            safety=FixSafety.MANUAL,
                        ),
                    )
                )
            if not re.search(r"(?m)^description:\s*.+$", text):
                findings.append(
                    Finding(
                        severity,
                        self.category,
                        messages.get(
                            "missingDescription", "agent missing description: {file}"
                        ).format(file=rel_path),
                        file=rel_path,
                        start_line=1,
                        start_column=1,
                        end_line=1,
                        end_column=1,
                        fix=FindingFix(
                            description="add description field",
                            safety=FixSafety.MANUAL,
                        ),
                    )
                )
        return findings


RULE: HarnessCheckRule = AgentFrontmatterRule()
