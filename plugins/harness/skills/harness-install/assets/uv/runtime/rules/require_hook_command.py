#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require hook command rule."""

from collections.abc import Iterable

import re
from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import is_safe_file, read_text, severity_for

STACK = "uv"

class RequireHookCommandRule(HarnessCheckRule):
    """Validate requireHookCommand check."""

    category = "requireHookCommand"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate requireHookCommand check."""
        result = []
        section = manifest.get(self.category, {})
        if isinstance(section, dict):
            params = section.get("parameters", {})
            if isinstance(params, dict):
                pre_push_path = params.get("prePushHook", "docs/harness/git-hooks/pre-push")
                pre_commit_path = params.get("preCommitHook", "docs/harness/git-hooks/pre-commit")
                allowed_cmds = params.get("allowedCommands", {})
                allowed_pre_commit_cmds = params.get("allowedPreCommitCommands", {})
                messages = section.get("messages", {})
                if isinstance(allowed_cmds, dict) and isinstance(messages, dict):
                    stack_commands = allowed_cmds.get(STACK)
                    if isinstance(stack_commands, list):
                        stack_pre_commit_commands = (
                            allowed_pre_commit_cmds.get(STACK, [])
                            if isinstance(allowed_pre_commit_cmds, dict)
                            else []
                        )
                        if not isinstance(stack_pre_commit_commands, list):
                            stack_pre_commit_commands = []
                        pre_push_file = root / (pre_push_path if isinstance(pre_push_path, str) else "docs/harness/git-hooks/pre-push")
                        pre_commit_file = root / (pre_commit_path if isinstance(pre_commit_path, str) else "docs/harness/git-hooks/pre-commit")
                        pre_push_text = read_text(pre_push_file) if is_safe_file(pre_push_file) else ""
                        pre_commit_text = read_text(pre_commit_file) if is_safe_file(pre_commit_file) else ""
                        command_match = re.search(r"# Harness validation command:\s*(.+)$", pre_push_text, re.MULTILINE)
                        declared_command = command_match.group(1).strip() if command_match else ""
                        if not declared_command:
                            result.append(Finding(
                                severity_for(manifest, self.category),
                                self.category,
                                messages.get("missingDeclaration", "pre-push hook must declare Harness validation command"),
                            ))
                        elif declared_command not in stack_commands:
                            result.append(Finding(
                                severity_for(manifest, self.category),
                                self.category,
                                messages.get("unsupportedCommand", "pre-push hook declares unsupported validation command: {command}").format(
                                    command=declared_command
                                ),
                            ))
                        if declared_command and declared_command not in pre_push_text.splitlines():
                            result.append(Finding(
                                severity_for(manifest, self.category),
                                self.category,
                                messages.get("commandNotRun", "pre-push hook must run the declared validation command"),
                            ))
                        if (
                            stack_pre_commit_commands
                            and not any(
                                re.search(rf"(^|\s)({re.escape(cmd)}|\s)(\s|$)", pre_commit_text)
                                for cmd in stack_pre_commit_commands
                            )
                            and re.search(
                                r"(^|\s)(uv|bun|gradle|mvn)(\s|$)|\./gradlew|harnessValidate|harness_validate\.py|harness-validate\.ts",
                                pre_commit_text,
                            )
                        ):
                            result.append(Finding(
                                severity_for(manifest, self.category),
                                self.category,
                                messages.get("preCommitMustNotRunFullStack", "pre-commit hook must not run full stack validation commands"),
                            ))
                        result.extend(
                            Finding(
                                severity_for(manifest, self.category),
                                self.category,
                                messages.get("ciCommandMatch", "{}: CI command mismatch — expected {command}").format(
                                    ci_file, command=declared_command
                                ),
                            )
                            for ci_file in [".github/workflows/harness.yml", ".gitlab-ci.yml"]
                            if is_safe_file(root / ci_file) and declared_command and declared_command not in read_text(root / ci_file)
                        )
                    else:
                        return [Finding("ERROR", self.category, f"validation command for stack '{STACK}' missing from manifest")]
        return result


RULE: HarnessCheckRule = RequireHookCommandRule()
