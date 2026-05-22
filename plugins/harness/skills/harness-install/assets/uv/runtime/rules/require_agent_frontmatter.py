#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require agent frontmatter rule."""

from collections.abc import Iterable

import re
from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import is_safe_directory, read_text, relative, safe_walk, severity_for

class RequireAgentFrontmatterRule(HarnessCheckRule):
    """Validate requireAgentFrontmatter check."""

    category = "requireAgentFrontmatter"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate requireAgentFrontmatter check."""
        section = manifest.get(self.category, {})
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
        try:
            name_pattern = re.compile(name_pattern_str)
        except re.error:
            return [
                Finding("ERROR", self.category, f"invalid namePattern regex: {name_pattern_str}"),
            ]
        dir_path = root / directory
        missing_dir_msg = messages.get("missingDirectory", ".claude/agents must contain at least one .md agent")
        if not is_safe_directory(dir_path):
            return [Finding(severity_for(manifest, self.category), self.category, missing_dir_msg)]
        files = tuple(sorted(p for p in safe_walk(dir_path) if p.parent == dir_path and p.suffix == ".md"))
        if not files:
            return [Finding(severity_for(manifest, self.category), self.category, missing_dir_msg)]
        result = []
        for path in files:
            text = read_text(path)
            if not text.startswith("---"):
                result.append(Finding(
                    severity_for(manifest, self.category),
                    self.category,
                    messages.get("missingFrontmatter", "agent missing frontmatter: {file}").format(file=relative(path)),
                ))
            if text.startswith("---") and not name_pattern.search(text):
                result.append(Finding(
                    severity_for(manifest, self.category),
                    self.category,
                    messages.get("missingName", "agent missing name: {file}").format(file=relative(path)),
                ))
            if not re.search(r"(?m)^description:\s*.+$", text):
                result.append(Finding(
                    severity_for(manifest, self.category),
                    self.category,
                    messages.get("missingDescription", "agent missing description: {file}").format(file=relative(path)),
                ))
        return result
