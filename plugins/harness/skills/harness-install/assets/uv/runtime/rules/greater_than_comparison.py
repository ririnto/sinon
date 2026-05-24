#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Forbid greater than comparison rule."""

from collections.abc import Iterable

from pathlib import Path

import libcst as cst

from harness_check_rule import Finding, HarnessCheckRule

from .utils import parse_python, relative, severity_for, stack_sources

class GreaterThanComparisonRule(HarnessCheckRule):
    """Validate greaterThanComparison check."""

    category = "greaterThanComparison"

    def applies(self, manifest: dict) -> bool:
        """Check if this rule applies to the manifest."""
        section = manifest.get(self.category)
        if not isinstance(section, dict):
            return False
        return section.get("enabled", True) is not False

    def validate(self, root: Path, manifest: dict) -> Iterable[Finding]:
        """Validate greaterThanComparison check."""
        severity = severity_for(manifest, self.category)
        category = self.category
        sources = stack_sources(root, manifest, self.category)
        class ComparisonFinder(cst.CSTVisitor):
            METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)
            def __init__(self) -> None:
                super().__init__()
                self.findings: list[Finding] = []
            def visit_Comparison(self, node: cst.Comparison) -> bool:
                for target in node.comparisons:
                    if isinstance(target.operator, (cst.GreaterThan, cst.GreaterThanEqual)):
                        pos = self.get_metadata(cst.metadata.PositionProvider, node)
                        self.findings.append(Finding(
                            severity,
                            category,
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
            visitor = ComparisonFinder()
            wrapper.visit(visitor)
            result.extend(visitor.findings)
        return result


RULE: HarnessCheckRule = GreaterThanComparisonRule()
