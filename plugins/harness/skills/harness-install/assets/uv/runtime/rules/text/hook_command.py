#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require hook command rule.
"""

import sys

from collections.abc import Iterable
import re

from rules.harness_check_rule import Finding, FindingFix, FixSafety, HarnessCheckRule
from core.rule_context import RuleContext, relative

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class HookCommandRule(HarnessCheckRule):
    """Validate hookCommand check."""

    category = "hookCommand"

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
        Validate hookCommand check.

        :param ctx: rule execution context.
        :returns: iterable of findings; empty when no issues are found.
        """
        result = []
        section = ctx.manifest.raw.get(self.category, {})
        if isinstance(section, dict):
            params = section.get("parameters", {})
            if isinstance(params, dict):
                pre_push_path = params.get(
                    "prePushHook", "docs/harness/git-hooks/pre-push"
                )
                pre_commit_path = params.get(
                    "preCommitHook", "docs/harness/git-hooks/pre-commit"
                )
                allowed_cmds = params.get("allowedCommands", [])
                allowed_pre_commit_cmds = params.get("allowedPreCommitCommands", [])
                messages = section.get("messages", {})
                if isinstance(allowed_cmds, list) and isinstance(messages, dict):
                    stack_commands = allowed_cmds
                    stack_pre_commit_commands = (
                        allowed_pre_commit_cmds
                        if isinstance(allowed_pre_commit_cmds, list)
                        else []
                    )
                    pre_push_path_str = (
                        pre_push_path
                        if isinstance(pre_push_path, str)
                        else "docs/harness/git-hooks/pre-push"
                    )
                    pre_commit_path_str = (
                        pre_commit_path
                        if isinstance(pre_commit_path, str)
                        else "docs/harness/git-hooks/pre-commit"
                    )
                    pre_push_text = (
                        ctx.read(pre_push_path_str)
                        if ctx.is_file(pre_push_path_str)
                        else ""
                    )
                    pre_commit_text = (
                        ctx.read(pre_commit_path_str)
                        if ctx.is_file(pre_commit_path_str)
                        else ""
                    )
                    command_match = re.search(
                        r"# Harness validation command:\s*(.+)$",
                        pre_push_text,
                        re.MULTILINE,
                    )
                    declared_command = (
                        command_match.group(1).strip() if command_match else ""
                    )
                    if not declared_command:
                        pre_push_relative = relative(
                            ctx.path_of(pre_push_path_str), ctx.root
                        )
                        result.append(
                            Finding(
                                ctx.severity_of(self.category),
                                self.category,
                                messages.get(
                                    "missingDeclaration",
                                    "pre-push hook must declare Harness validation command",
                                ),
                                file=pre_push_relative,
                                start_line=1,
                                start_column=1,
                                end_line=1,
                                end_column=1,
                                fix=FindingFix(
                                    description="add '# Harness validation command: <command>' line to pre-push hook",
                                    safety=FixSafety.MANUAL,
                                    edits=(),
                                ),
                            )
                        )
                    elif declared_command not in stack_commands:
                        pre_push_relative = relative(
                            ctx.path_of(pre_push_path_str), ctx.root
                        )
                        result.append(
                            Finding(
                                ctx.severity_of(self.category),
                                self.category,
                                messages.get(
                                    "unsupportedCommand",
                                    "pre-push hook declares unsupported validation command: {command}",
                                ).format(command=declared_command),
                                file=pre_push_relative,
                                start_line=1,
                                start_column=1,
                                end_line=1,
                                end_column=1,
                                fix=FindingFix(
                                    description=f"replace validation command with one from allowed list: {', '.join(stack_commands)}",
                                    safety=FixSafety.MANUAL,
                                    edits=(),
                                ),
                            )
                        )
                    if (
                        declared_command
                        and declared_command not in pre_push_text.splitlines()
                    ):
                        pre_push_relative = relative(
                            ctx.path_of(pre_push_path_str), ctx.root
                        )
                        result.append(
                            Finding(
                                ctx.severity_of(self.category),
                                self.category,
                                messages.get(
                                    "commandNotRun",
                                    "pre-push hook must run the declared validation command",
                                ),
                                file=pre_push_relative,
                                start_line=1,
                                start_column=1,
                                end_line=1,
                                end_column=1,
                                fix=FindingFix(
                                    description=f"add line to pre-push hook that runs: {declared_command}",
                                    safety=FixSafety.MANUAL,
                                    edits=(),
                                ),
                            )
                        )
                    if (
                        stack_pre_commit_commands
                        and not any(
                            re.search(
                                rf"(^|\s)({re.escape(cmd)}|\s)(\s|$)",
                                pre_commit_text,
                            )
                            for cmd in stack_pre_commit_commands
                        )
                        and re.search(
                            r"(^|\s)(uv|bun|gradle|mvn)(\s|$)|\./gradlew|harnessCheck|harness_check\.py|harness-check\.ts|harness-check\.sh",
                            pre_commit_text,
                        )
                    ):
                        pre_commit_relative = relative(
                            ctx.path_of(pre_commit_path_str), ctx.root
                        )
                        result.append(
                            Finding(
                                ctx.severity_of(self.category),
                                self.category,
                                messages.get(
                                    "preCommitMustNotRunFullStack",
                                    "pre-commit hook must not run full stack validation commands",
                                ),
                                file=pre_commit_relative,
                                start_line=1,
                                start_column=1,
                                end_line=1,
                                end_column=1,
                                fix=FindingFix(
                                    description=f"replace full-stack validation with one of: {', '.join(stack_pre_commit_commands)}",
                                    safety=FixSafety.MANUAL,
                                    edits=(),
                                ),
                            )
                        )
                    for ci_file in [
                        ".github/workflows/harness.yml",
                        ".gitlab-ci.yml",
                    ]:
                        if ctx.is_file(ci_file) and declared_command and declared_command not in ctx.read(ci_file):
                            ci_relative = relative(
                                ctx.path_of(ci_file), ctx.root
                            )
                            result.append(
                                Finding(
                                    ctx.severity_of(self.category),
                                    self.category,
                                    messages.get(
                                        "ciCommandMatch",
                                        "{}: CI command mismatch — expected {command}",
                                    ).format(ci_file, command=declared_command),
                                    file=ci_relative,
                                    start_line=1,
                                    start_column=1,
                                    end_line=1,
                                    end_column=1,
                                    fix=FindingFix(
                                        description=f"add line to CI that runs: {declared_command}",
                                        safety=FixSafety.MANUAL,
                                        edits=(),
                                    ),
                                )
                            )
                else:
                    return [
                        Finding(
                            "ERROR",
                            self.category,
                            "validation command not found in manifest or wrong type",
                            file=None,
                            start_line=None,
                            start_column=None,
                            end_line=None,
                            end_column=None,
                            fix=None,
                        )
                    ]
        return result


RULE: HarnessCheckRule = HookCommandRule()
