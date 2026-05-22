#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require skill frontmatter rule."""

from collections.abc import Iterable

import re
from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import is_safe_directory, read_text, relative, safe_walk, severity_for

class RequireSkillFrontmatterRule(HarnessCheckRule):
    """Validate requireSkillFrontmatter check."""

    category = "requireSkillFrontmatter"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate requireSkillFrontmatter check."""
        section = manifest.get(self.category, {})
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
        dir_path = root / root_directory
        missing_dir_msg = messages.get("missingDirectory", ".claude/skills must contain at least one SKILL.md")
        if not is_safe_directory(dir_path):
            return [Finding(severity_for(manifest, self.category), self.category, missing_dir_msg)]
        files = tuple(sorted(p for p in safe_walk(dir_path) if p.name == filename))
        if not files:
            return [Finding(severity_for(manifest, self.category), self.category, missing_dir_msg)]
        result = []
        for path in files:
            text = read_text(path)
            if not text.startswith("---"):
                result.append(Finding(
                    severity_for(manifest, self.category),
                    self.category,
                    messages.get("missingFrontmatter", "skill missing frontmatter: {file}").format(file=relative(path)),
                ))
            for field in required_fields:
                if isinstance(field, str) and not re.search(rf"(?m)^{re.escape(field)}:\s*.+$", text):
                    result.append(Finding(
                        severity_for(manifest, self.category),
                        self.category,
                        messages.get("missingDescription", f"skill missing {field}: {{file}}").format(file=relative(path)),
                    ))
        return result
