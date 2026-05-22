#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require files exist rule."""

from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import is_safe_file, severity_for


class RequireFilesExistRule(HarnessCheckRule):
    """Validate requireFilesExist check."""

    category = "requireFilesExist"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> list[Finding]:
        """Validate requireFilesExist check."""
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        paths = params.get("paths", [])
        if not isinstance(paths, list):
            return []
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        template = messages.get("default", "missing file: {path}")
        return [
            Finding(
                severity_for(manifest, self.category),
                self.category,
                template.format(path=path),
            )
            for path in paths
            if isinstance(path, str) and not is_safe_file(root / path)
        ]
