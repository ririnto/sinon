#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Forbid unstructured logging rule."""

from collections.abc import Iterable

from pathlib import Path

import libcst as cst

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import parse_python, relative, severity_for, stack_sources

class ForbidUnstructuredLoggingRule(HarnessCheckRule):
    """Validate forbidUnstructuredLogging check."""

    category = "forbidUnstructuredLogging"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate forbidUnstructuredLogging check."""
        severity = severity_for(manifest, self.category)
        sources = stack_sources(root, manifest, self.category)
        class _PrintFinder(cst.CSTVisitor):
            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path
            def visit_Call(self, node: cst.Call) -> bool:
                if isinstance(node.func, cst.Name):
                    if node.func.value == "print":
                        pos = self.get_metadata(cst.metadata.PositionProvider, node)
                        self.findings.append(Finding(
                            severity,
                            self.category,
                            f"{self.rel_path}:{pos.start.line}: unstructured logging `print`; use structured logger",
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
            visitor = _PrintFinder(relative(path))
            wrapper.visit(visitor)
            result.extend(visitor.findings)
        return result
