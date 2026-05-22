#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require doc content rule."""

from collections.abc import Iterable

from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import is_safe_file, read_text, severity_for

class RequireDocContentRule(HarnessCheckRule):
    """Validate requireDocContent check."""

    category = "requireDocContent"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate requireDocContent check."""
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        checks = params.get("checks", [])
        if not isinstance(checks, list):
            return []
        valid_checks = [
            check
            for check in checks
            if isinstance(check, dict)
            and isinstance(check.get("files"), list)
            and isinstance(check.get("containsAll"), list)
            and isinstance(check.get("failureMessage"), str)
        ]
        return [
            Finding(
                severity_for(manifest, self.category),
                self.category,
                check.get("failureMessage"),
            )
            for check in valid_checks
            if not all(
                isinstance(s, str) and s in "\n".join(
                    read_text(root / f)
                    for f in check.get("files", [])
                    if isinstance(f, str) and is_safe_file(root / f)
                )
                for s in check.get("containsAll", [])
            )
        ]


RULE: HarnessCheckRule = RequireDocContentRule()
