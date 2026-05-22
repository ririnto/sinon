#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require CI command matches hook rule."""

from collections.abc import Iterable

import re
from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import is_safe_file, read_text, severity_for

class RequireCiCommandMatchesHookRule(HarnessCheckRule):
    """Validate requireCiCommandMatchesHook check."""

    category = "requireCiCommandMatchesHook"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate requireCiCommandMatchesHook check."""
        result = []
        section = manifest.get(self.category, {})
        if isinstance(section, dict):
            params = section.get("parameters", {})
            if isinstance(params, dict):
                ci_files = params.get("ciFiles", [])
                reference_hook = params.get("referenceHook", "")
                messages = section.get("messages", {})
                if (isinstance(ci_files, list) and isinstance(reference_hook, str)
                        and isinstance(messages, dict)):
                    reference_hook_path = root / reference_hook
                    if is_safe_file(reference_hook_path):
                        hook_text = read_text(reference_hook_path)
                        command_match = re.search(r"# Harness validation command:\s*(.+)$", hook_text, re.MULTILINE)
                        command = command_match.group(1).strip() if command_match else ""
                        if command:
                            for ci_file in ci_files:
                                if isinstance(ci_file, str):
                                    ci_file_path = root / ci_file
                                    if is_safe_file(ci_file_path):
                                        ci_text = read_text(ci_file_path)
                                        if command not in ci_text:
                                            result.append(Finding(
                                                severity_for(manifest, self.category),
                                                self.category,
                                                messages.get("default", f"{ci_file}: CI command mismatch — expected {command}"),
                                            ))
        return result


RULE: HarnessCheckRule = RequireCiCommandMatchesHookRule()
