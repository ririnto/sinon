#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Forbid unstructured logging rule."""

from collections.abc import Iterable

from pathlib import Path

import libcst as cst

from harness_check_rule import Finding, HarnessCheckRule

from .utils import parse_python, relative, severity_for, stack_sources

class UnstructuredLoggingRule(HarnessCheckRule):
    """Validate unstructuredLogging check."""

    category = "unstructuredLogging"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate unstructuredLogging check."""
        severity = severity_for(manifest, self.category)
        category = self.category
        sources = stack_sources(root, manifest, self.category)
        class PrintFinder(cst.CSTVisitor):
            METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)
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
                            category,
                            f"{self.rel_path}:{pos.start.line}: unstructured logging `print`; use structured logger",
                        ))
                return True
        result = []
        for path in sources:
            tree, error = parse_python(path)
            if error is not None:
                result.append(Finding(
                    severity,
                    category,
                    f"{relative(path)}: syntax error: {error}",
                ))
                continue
            wrapper = cst.MetadataWrapper(tree)
            visitor = PrintFinder(relative(path))
            wrapper.visit(visitor)
            result.extend(visitor.findings)
        return result


RULE: HarnessCheckRule = UnstructuredLoggingRule()
