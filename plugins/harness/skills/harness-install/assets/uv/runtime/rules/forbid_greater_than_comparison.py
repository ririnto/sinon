#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Forbid greater than comparison rule."""

from pathlib import Path

import libcst as cst

from harness_check_rule import Finding, HarnessCheckRule

from ._utils import parse_python, relative, severity_for, stack_sources


class ForbidGreaterThanComparisonRule(HarnessCheckRule):
    """Validate forbidGreaterThanComparison check."""

    category = "forbidGreaterThanComparison"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        enabled = section.get("enabled", True)
        return enabled is not False

    def validate(self, root: Path, manifest: dict) -> list[Finding]:
        """Validate forbidGreaterThanComparison check."""
        severity = severity_for(manifest, self.category)
        sources = stack_sources(root, manifest, self.category)
        class _ComparisonFinder(cst.CSTVisitor):
            def __init__(self) -> None:
                super().__init__()
                self.findings: list[Finding] = []
            def visit_Comparison(self, node: cst.Comparison) -> bool:
                for target in node.comparators:
                    if isinstance(target.operator, (cst.GreaterThan, cst.GreaterThanEqual)):
                        pos = self.get_metadata(cst.metadata.PositionProvider, node)
                        self.findings.append(Finding(
                            severity,
                            self.category,
                            f"{relative(path)}:{pos.start.line}: forbidden `>`/`>=` comparison; rewrite with `<`/`<=` so the smaller value is on the left",
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
            visitor = _ComparisonFinder()
            wrapper.visit(visitor)
            result.extend(visitor.findings)
        return result
