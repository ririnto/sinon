#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require single top level kotlin declaration rule."""

from collections.abc import Iterable

import re
from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import is_safe_directory, read_text, relative, safe_walk, severity_for

class RequireSingleTopLevelKotlinDeclarationRule(HarnessCheckRule):
    """Validate requireSingleTopLevelKotlinDeclaration check."""

    category = "requireSingleTopLevelKotlinDeclaration"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate requireSingleTopLevelKotlinDeclaration check."""
        section = manifest.get(self.category, {})
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
        top_level_pattern = re.compile(
            r"^(class|interface|enum class|data class|sealed class|object|abstract class|val|var|fun|typealias)\b",
            re.MULTILINE,
        )
        result = []
        for directory in directories:
            if not isinstance(directory, str):
                continue
            dir_path = root / directory
            if not is_safe_directory(dir_path):
                continue
            for path in safe_walk(dir_path):
                if path.suffix != ".kt":
                    continue
                text = read_text(path)
                declaration_count = len(top_level_pattern.findall(text))
                if declaration_count != 1:
                    result.append(Finding(
                        severity_for(manifest, self.category),
                        self.category,
                        template.format(file=relative(path)),
                    ))
        return result


RULE: HarnessCheckRule = RequireSingleTopLevelKotlinDeclarationRule()
