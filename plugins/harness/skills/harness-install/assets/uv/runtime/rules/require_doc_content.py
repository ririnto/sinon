#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require doc content rule."""

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

    def validate(self, root: Path, manifest: dict) -> list[Finding]:
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
        result = []
        for check in checks:
            if not isinstance(check, dict):
                continue
            if not isinstance(check.get("files"), list):
                continue
            if not isinstance(check.get("containsAll"), list):
                continue
            if not isinstance(check.get("failureMessage"), str):
                continue
            files = check.get("files", [])
            contains_all = check.get("containsAll", [])
            failure_message = check.get("failureMessage")
            combined_text = "\n".join(
                read_text(root / f)
                for f in files
                if isinstance(f, str) and is_safe_file(root / f)
            )
            if not all(
                isinstance(s, str) and s in combined_text
                for s in contains_all
            ):
                result.append(Finding(
                    severity_for(manifest, self.category),
                    self.category,
                    failure_message,
                ))
        return result
