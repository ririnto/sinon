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
        valid_hooks = [
            (hook, read_text(root / hook), Path(hook).name)
            for hook in hooks
            if isinstance(hook, str) and is_safe_file(root / hook)
        ]
        findings = [
            Finding(
                severity_for(manifest, self.category),
                self.category,
                messages.get("missingMarker", "{hook} must contain generated marker '{marker}'").format(
                    hook=hook, marker=marker_template.format(name=hook_name)
                ),
            )
            for hook, text, hook_name in valid_hooks
            if marker_template.format(name=hook_name) not in text
        ]
        findings.extend(
            Finding(
                severity_for(manifest, self.category),
                self.category,
                messages.get("placeholderPresent", "{hook} still contains packaging placeholder text").format(hook=hook),
            )
            for hook, text, _ in valid_hooks
            if isinstance(placeholder_forbidden, str) and placeholder_forbidden in text
        )
        return findings


RULE: HarnessCheckRule = RequireHookGeneratedMarkerRule()
