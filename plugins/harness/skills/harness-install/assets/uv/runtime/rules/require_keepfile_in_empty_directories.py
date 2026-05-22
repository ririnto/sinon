#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require keepfile in empty directories rule."""

from collections.abc import Iterable

from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import is_safe_directory, is_safe_file, severity_for

class RequireKeepleInEmptyDirectoriesRule(HarnessCheckRule):
    """Validate requireKeepfileInEmptyDirectories check."""

    category = "requireKeepfileInEmptyDirectories"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate requireKeepfileInEmptyDirectories check."""
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
            "default", "empty directory must keep placeholder or real files: {directory}"
        )
        return [
            Finding(
                severity_for(manifest, self.category),
                self.category,
                template.format(directory=directory),
            )
            for directory in directories
            if isinstance(directory, str)
            and is_safe_directory(root / directory)
            and not any(p for p in (root / directory).iterdir() if p.name != ".gitkeep")
            and not is_safe_file(root / directory / ".gitkeep")
        ]
