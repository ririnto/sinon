#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Forbid early return rule."""

from collections.abc import Iterable

from pathlib import Path

import libcst as cst

from harness_check_rule import Finding, HarnessCheckRule

from .utils import has_nested_function, parse_python, relative, severity_for, stack_sources

class EarlyReturnRule(HarnessCheckRule):
    """Validate earlyReturn check."""

    category = "earlyReturn"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate earlyReturn check."""
        severity = severity_for(manifest, self.category)
        category = self.category
        sources = stack_sources(root, manifest, self.category)
        class EarlyReturnFinder(cst.CSTVisitor):
            METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)
            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path
            def visit_FunctionDef(self, node: cst.FunctionDef) -> bool:
                if has_nested_function(node):
                    return False
                if not isinstance(node.body, cst.IndentedBlock):
                    return True
                if not node.body.body:
                    return True
                for i, stmt in enumerate(node.body.body[:-1]):
                    if isinstance(stmt, cst.SimpleStatementLine):
                        for inner_stmt in stmt.body:
                            if isinstance(inner_stmt, cst.Return):
                                pos = self.get_metadata(cst.metadata.PositionProvider, stmt)
                                self.findings.append(Finding(
                                    severity,
                                    category,
                                    f"{self.rel_path}:{pos.start.line}: function `{node.name.value}` has an early/mid return; restructure with single exit",
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
            visitor = EarlyReturnFinder(relative(path))
            wrapper.visit(visitor)
            result.extend(visitor.findings)
        return result


RULE: HarnessCheckRule = EarlyReturnRule()
