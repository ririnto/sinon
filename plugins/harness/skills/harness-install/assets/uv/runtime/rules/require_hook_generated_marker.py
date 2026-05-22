#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require hook generated marker rule."""

from collections.abc import Iterable

from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import is_safe_file, read_text, severity_for

class RequireHookGeneratedMarkerRule(HarnessCheckRule):
    """Validate requireHookGeneratedMarker check."""

    category = "requireHookGeneratedMarker"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate requireHookGeneratedMarker check."""
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        hooks = params.get("hooks", [])
        if not isinstance(hooks, list):
            return []
        marker_template = params.get("markerTemplate", "# Harness generated hook: {name}")
        placeholder_forbidden = params.get("placeholderForbidden", "packaged placeholder is replaced during harness installation")
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        result = []
        for hook in hooks:
            if not isinstance(hook, str) or not is_safe_file(root / hook):
                continue
            text = read_text(root / hook)
            hook_name = Path(hook).name
            expected_marker = marker_template.format(name=hook_name)
            if expected_marker not in text:
                result.append(Finding(
                    severity_for(manifest, self.category),
                    self.category,
                    messages.get("missingMarker", "{hook} must contain generated marker '{marker}'").format(
                        hook=hook, marker=expected_marker
                    ),
                ))
            if isinstance(placeholder_forbidden, str) and placeholder_forbidden in text:
                result.append(Finding(
                    severity_for(manifest, self.category),
                    self.category,
                    messages.get("placeholderPresent", "{hook} still contains packaging placeholder text").format(hook=hook),
                ))
        return result
