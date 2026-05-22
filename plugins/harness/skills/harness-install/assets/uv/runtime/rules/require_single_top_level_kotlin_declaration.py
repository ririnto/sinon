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
        return section.get("enabled", True) is not False

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
        return [
            Finding(
                severity_for(manifest, self.category),
                self.category,
                template.format(file=relative(path)),
            )
            for directory in directories
            if isinstance(directory, str)
            and is_safe_directory(root / directory)
            for path in safe_walk(root / directory)
            if path.suffix == ".kt"
            and len(top_level_pattern.findall(read_text(path))) != 1
        ]


RULE: HarnessCheckRule = RequireSingleTopLevelKotlinDeclarationRule()
