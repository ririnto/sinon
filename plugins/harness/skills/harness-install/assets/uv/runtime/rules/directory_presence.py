#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require directories exist rule."""

from collections.abc import Iterable

from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from .utils import is_safe_directory, severity_for

class DirectoryPresenceRule(HarnessCheckRule):
    """Validate directoryPresence check."""

    category = "directoryPresence"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate directoryPresence check."""
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
        template = messages.get("default", "missing directory: {path}")
        return [
            Finding(
                severity_for(manifest, self.category),
                self.category,
                template.format(path=path),
            )
            for path in paths
            if isinstance(path, str) and not is_safe_directory(root / path)
        ]


RULE: HarnessCheckRule = DirectoryPresenceRule()
