#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Forbid unchecked tasks under rule."""

from collections.abc import Iterable

import fnmatch
import re
from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import is_safe_directory, read_text, relative, severity_for

class ForbidUncheckedTasksUnderRule(HarnessCheckRule):
    """Validate forbidUncheckedTasksUnder check."""

    category = "forbidUncheckedTasksUnder"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate forbidUncheckedTasksUnder check."""
        section = manifest.get(self.category, {})
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
        dir_path = root / directory
        if not is_safe_directory(dir_path):
            return []
        try:
            unchecked_pattern = re.compile(unchecked_pattern_str)
        except re.error:
            return [Finding("ERROR", self.category, f"invalid uncheckedTaskPattern regex: {unchecked_pattern_str}")]
        pattern_glob = filename_pattern_str if isinstance(filename_pattern_str, str) else "*.md"
        result = []
        for path in sorted(dir_path.iterdir()):
            if not path.is_file():
                continue
            if path.name == ".gitkeep":
                continue
            if not fnmatch.fnmatch(path.name, pattern_glob):
                continue
            text = read_text(path)
            if unchecked_pattern.search(text):
                result.append(Finding(
                    severity_for(manifest, self.category),
                    self.category,
                    template.format(file=relative(path)),
                ))
        return result


RULE: HarnessCheckRule = ForbidUncheckedTasksUnderRule()
