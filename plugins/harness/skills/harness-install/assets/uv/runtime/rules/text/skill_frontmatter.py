#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require skill frontmatter rule.
"""

import sys

from collections.abc import Iterable
import re

from rules.harness_check_rule import Finding, FindingFix, FixSafety, HarnessCheckRule
from core.rule_context import RuleContext, relative

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class SkillFrontmatterRule(HarnessCheckRule):
    """Validate skillFrontmatter check."""

    category = "skillFrontmatter"

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
        Validate skillFrontmatter check.

        :param ctx: rule execution context.
        :returns: iterable of findings; empty when no issues are found.
        """
        section = ctx.manifest.raw.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        root_directory = params.get("rootDirectory", ".claude/skills")
        filename = params.get("filename", "SKILL.md")
        required_fields = params.get("requiredFields", [])
        if not isinstance(required_fields, list):
            return []
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        missing_dir_msg = messages.get(
            "missingDirectory", ".claude/skills must contain at least one SKILL.md"
        )
        if not ctx.is_directory(root_directory):
            return [
                Finding(
                    ctx.severity_of(self.category),
                    self.category,
                    missing_dir_msg,
                    file=root_directory,
                    start_line=1,
                    start_column=1,
                    end_line=1,
                    end_column=1,
                    fix=FindingFix(
                        description="create skills directory",
                        safety=FixSafety.MANUAL,
                    ),
                )
            ]
        ctx.root / root_directory
        files = tuple(
            sorted(
                p for p in ctx.walk_directory(root_directory)[0] if p.name == filename
            )
        )
        if not files:
            return [
                Finding(
                    ctx.severity_of(self.category),
                    self.category,
                    missing_dir_msg,
                    file=root_directory,
                    start_line=1,
                    start_column=1,
                    end_line=1,
                    end_column=1,
                    fix=FindingFix(
                        description=f"create {filename} file",
                        safety=FixSafety.MANUAL,
                    ),
                )
            ]
        severity = ctx.severity_of(self.category)
        findings = []
        for path in files:
            rel_path_str = path.relative_to(ctx.root).as_posix()
            text = ctx.read(rel_path_str)
            rel_path = relative(path, ctx.root)
            if not text.startswith("---"):
                findings.append(
                    Finding(
                        severity,
                        self.category,
                        messages.get(
                            "missingFrontmatter", "skill missing frontmatter: {file}"
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
            for field in required_fields:
                if isinstance(field, str) and not re.search(
                    rf"(?m)^{re.escape(field)}:\s*.+$", text
                ):
                    msg_template = "skill missing " + field + ": {file}"
                    findings.append(
                        Finding(
                            severity,
                            self.category,
                            messages.get("missingDescription", msg_template).format(
                                file=rel_path
                            ),
                            file=rel_path,
                            start_line=1,
                            start_column=1,
                            end_line=1,
                            end_column=1,
                            fix=FindingFix(
                                description=f"add {field} field",
                                safety=FixSafety.MANUAL,
                            ),
                        )
                    )
        return findings


RULE: HarnessCheckRule = SkillFrontmatterRule()
