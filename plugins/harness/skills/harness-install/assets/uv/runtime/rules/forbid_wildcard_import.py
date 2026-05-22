#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Forbid wildcard import rule."""

from collections.abc import Iterable

from pathlib import Path

import libcst as cst

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import parse_python, relative, severity_for, stack_sources

class ForbidWildcardImportRule(HarnessCheckRule):
    """Validate forbidWildcardImport check."""

    category = "forbidWildcardImport"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate forbidWildcardImport check."""
        severity = severity_for(manifest, self.category)
        sources = stack_sources(root, manifest, self.category)
        class _WildcardFinder(cst.CSTVisitor):
            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path
            def visit_ImportFrom(self, node: cst.ImportFrom) -> bool:
                if isinstance(node.names, cst.ImportStar):
                    pos = self.get_metadata(cst.metadata.PositionProvider, node)
                    module_parts = []
                    if isinstance(node.module, cst.Attribute):
                        current = node.module
                        parts = [current.attr.value]
                        while isinstance(current.value, cst.Attribute):
                            current = current.value
                            parts.append(current.attr.value)
                        if isinstance(current.value, cst.Name):
                            parts.append(current.value.value)
                        module_parts = list(reversed(parts))
                    elif isinstance(node.module, cst.Name):
                        module_parts = [node.module.value]
                    module_str = ".".join(module_parts) if module_parts else "?"
                    self.findings.append(Finding(
                        severity,
                        self.category,
                        f"{self.rel_path}:{pos.start.line}: wildcard import `from {module_str} import *` forbidden; import explicit symbols",
                    ))
                return True
        result = []
        for path in sources:
            tree, error = parse_python(path)
            if error is not None:
                result.append(Finding(
                    severity,
                    self.category,
                    f"{relative(path)}: syntax error: {error}",
                ))
                continue
            wrapper = cst.MetadataWrapper(tree)
            visitor = _WildcardFinder(relative(path))
            wrapper.visit(visitor)
            result.extend(visitor.findings)
        return result


RULE: HarnessCheckRule = ForbidWildcardImportRule()
