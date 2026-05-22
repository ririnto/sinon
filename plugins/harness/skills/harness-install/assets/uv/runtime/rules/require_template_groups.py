#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require template groups rule."""

from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import is_safe_directory, severity_for


class RequireTemplateGroupsRule(HarnessCheckRule):
    """Validate requireTemplateGroups check."""

    category = "requireTemplateGroups"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> list[Finding]:
        """Validate requireTemplateGroups check."""
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        target_root = params.get("targetRoot", "docs/harness/templates")
        groups = params.get("groups", [])
        if not isinstance(groups, list):
            return []
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        template = messages.get("default", "missing template group: {targetRoot}/{group}")
        return [
            Finding(
                severity_for(manifest, self.category),
                self.category,
                template.format(targetRoot=target_root, group=group),
            )
            for group in groups
            if isinstance(group, str) and not is_safe_directory(root / target_root / group)
        ]
