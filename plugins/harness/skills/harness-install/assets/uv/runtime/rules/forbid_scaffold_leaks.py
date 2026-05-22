#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Forbid scaffold leaks rule."""

from collections.abc import Iterable

import re
from pathlib import Path

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import read_text, relative, safe_file_or_walk, severity_for

class ForbidScaffoldLeaksRule(HarnessCheckRule):
    """Validate forbidScaffoldLeaks check."""

    category = "forbidScaffoldLeaks"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate forbidScaffoldLeaks check."""
        section = manifest.get(self.category, {})
        if not isinstance(section, dict):
            return []
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return []
        scope = params.get("scope", {})
        if not isinstance(scope, dict):
            return []
        bases_data = scope.get("bases", [])
        excluded_data = scope.get("excludedSubtrees", [])
        exts_data = scope.get("extensions", [])
        if not isinstance(bases_data, list):
            return []
        active_roots = tuple(root / b for b in bases_data if isinstance(b, str))
        excluded_paths = tuple(
            root / e for e in (excluded_data if isinstance(excluded_data, list) else ())
            if isinstance(e, str)
        )
        extensions = frozenset(
            f".{ext}" for ext in (exts_data if isinstance(exts_data, list) else [])
            if isinstance(ext, str)
        ) or frozenset({".md", ".txt", ".json", ".yml", ".yaml"})
        patterns_data = params.get("patterns", [])
        if not isinstance(patterns_data, list):
            return []
        compiled_patterns = tuple(
            (re.compile(item.get("pattern")), item.get("label"))
            for item in patterns_data
            if isinstance(item, dict)
            and isinstance(item.get("pattern"), str)
            and isinstance(item.get("label"), str)
            and (lambda p=item.get("pattern"): True if re.compile(p) else False)()
        )
        messages = section.get("messages", {})
        if not isinstance(messages, dict):
            return []
        return [
            Finding(
                severity_for(manifest, self.category),
                self.category,
                messages.get("default", "{label} in active asset: {file}").format(
                    label=label, file=relative(path)
                ),
            )
            for base in active_roots
            for path in safe_file_or_walk(base)
            if path.is_file()
            and path.suffix in extensions
            and not any(path == ex or ex in path.parents for ex in excluded_paths)
            for pattern, label in compiled_patterns
            if pattern.search(read_text(path))
        ]


RULE: HarnessCheckRule = ForbidScaffoldLeaksRule()
