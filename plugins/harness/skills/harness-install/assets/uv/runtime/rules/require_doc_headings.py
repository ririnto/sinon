#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require doc headings rule."""

from collections.abc import Iterable

from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import is_safe_file, read_text, severity_for

class RequireDocHeadingsRule(HarnessCheckRule):
    """Validate requireDocHeadings check."""

    category = "requireDocHeadings"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate requireDocHeadings check."""
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        source_from = params.get("sourceFilesFromCategory")
        if not isinstance(source_from, str):
            return []
        source_section = manifest.get(source_from, {})
        if not isinstance(source_section, dict):
            return []
        source_params = source_section.get("parameters", {})
        if not isinstance(source_params, dict):
            return []
        source_paths = source_params.get("paths", [])
        if not isinstance(source_paths, list):
            return []
        source_filter = params.get("sourceFilter", {})
        if not isinstance(source_filter, dict):
            return []
        prefix = source_filter.get("prefix", "")
        suffix = source_filter.get("suffix", "")
        filtered_files = tuple(
            p for p in source_paths
            if isinstance(p, str) and p.startswith(prefix) and p.endswith(suffix)
        )
        headings = params.get("headings", [])
        if not isinstance(headings, list):
            return []
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        template = messages.get("default", "doc missing {heading}: {file}")
        return [
            Finding(
                severity_for(manifest, self.category),
                self.category,
                template.format(heading=heading, file=file_path),
            )
            for file_path in filtered_files
            if is_safe_file(root / file_path)
            for heading in headings
            if isinstance(heading, str) and heading not in read_text(root / file_path)
        ]


RULE: HarnessCheckRule = RequireDocHeadingsRule()
