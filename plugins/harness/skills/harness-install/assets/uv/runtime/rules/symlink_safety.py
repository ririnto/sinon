#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Forbid unsafe symlinks rule."""

from collections.abc import Iterable

import os
from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from .utils import is_safe_directory, relative, safe_walk, severity_for

class SymlinkSafetyRule(HarnessCheckRule):
    """Validate symlinkSafety check."""

    category = "symlinkSafety"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate symlinkSafety check."""
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        allowed_pairs = params.get("allowedSymlinkPairs", [])
        if not isinstance(allowed_pairs, list):
            return []
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        allowed_set = frozenset(
            tuple(sorted((p[0], p[1]))) for p in allowed_pairs
            if isinstance(p, list) and len(p) == 2
        )
        scan_bases = (".claude", "docs", ".github", "AGENTS.md", "CLAUDE.md", "ARCHITECTURE.md")
        root_findings = [
            Finding(
                severity_for(manifest, self.category),
                self.category,
                messages.get("scanRootNotAllowed", "symlink scan root is not allowed: {path}").format(
                    path=relative(base_path)
                ),
            )
            for base in scan_bases
            if (base_path := root / base).is_symlink()
            and tuple(sorted((base_path.name, os.readlink(base_path).split("/")[-1]))) not in allowed_set
        ]
        file_findings = []
        for base in scan_bases:
            base_path = root / base
            if base_path.is_symlink() or not is_safe_directory(base_path):
                continue
            for path in safe_walk(base_path):
                if not path.is_symlink():
                    continue
                if path.parent == root and path.name in {"AGENTS.md", "CLAUDE.md"}:
                    target_name = os.readlink(path)
                    pair = tuple(sorted((path.name, target_name)))
                    if pair not in allowed_set:
                        file_findings.append(Finding(
                            severity_for(manifest, self.category),
                            self.category,
                            messages.get("fileNotAllowed", "symlink file is not allowed: {path}").format(
                                path=relative(path)
                            ),
                        ))
                else:
                    file_findings.append(Finding(
                        severity_for(manifest, self.category),
                        self.category,
                        messages.get("pathNotAllowed", "symlink path is not allowed: {path}").format(
                            path=relative(path)
                        ),
                    ))
        return root_findings + file_findings


RULE: HarnessCheckRule = SymlinkSafetyRule()
