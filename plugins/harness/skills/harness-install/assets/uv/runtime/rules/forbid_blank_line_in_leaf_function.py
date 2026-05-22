#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Forbid blank line in leaf function rule."""

from pathlib import Path

import libcst as cst

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import has_nested_function, parse_python, relative, severity_for, stack_sources


class ForbidBlankLineInLeafFunctionRule(HarnessCheckRule):
    """Validate forbidBlankLineInLeafFunction check."""

    category = "forbidBlankLineInLeafFunction"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> list[Finding]:
        """Validate forbidBlankLineInLeafFunction check."""
        severity = severity_for(manifest, self.category)
        sources = stack_sources(root, manifest, self.category)
        class _BlankLineFinder(cst.CSTVisitor):
            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path
            def visit_FunctionDef(self, node: cst.FunctionDef) -> bool:
                if has_nested_function(node):
                    return False
                if not isinstance(node.body, cst.IndentedBlock):
                    return False
                func_name = node.name.value
                for stmt in node.body.body:
                    if isinstance(stmt, cst.EmptyLine) and stmt.comment is None:
                        pos = self.get_metadata(cst.metadata.PositionProvider, stmt)
                        self.findings.append(Finding(
                            severity,
                            self.category,
                            f"{self.rel_path}:{pos.start.line}: leaf function `{func_name}` contains a blank line; remove or extract the section",
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
            visitor = _BlankLineFinder(relative(path))
            wrapper.visit(visitor)
            result.extend(visitor.findings)
        return result
