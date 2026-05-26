#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Forbid greater than comparison rule.
"""

import sys


from collections.abc import Iterable

import libcst as cst

from rules.harness_check_rule import (
    Finding,
    FindingFix,
    FixSafety,
    HarnessCheckRule,
    parse_python,
)
from core.rule_context import RuleContext, relative, stack_sources_configured

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class GreaterThanComparisonRule(HarnessCheckRule):
    """Validate greaterThanComparison check."""

    category = "greaterThanComparison"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        if not ctx.is_enabled(self.category):
            return False
        return stack_sources_configured(ctx.manifest.raw, self.category)

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate greaterThanComparison check."""
        severity = ctx.severity_of(self.category)
        category = self.category
        sources = ctx.stack_sources(self.category)

        class ComparisonFinder(cst.CSTVisitor):
            METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)

            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path

            def visit_Comparison(self, node: cst.Comparison) -> bool:
                for target in node.comparisons:
                    if isinstance(target.operator, cst.GreaterThan):
                        pos = self.get_metadata(cst.metadata.PositionProvider, node)
                        self.findings.append(
                            Finding(
                                severity,
                                category,
                                f"{self.rel_path}:{pos.start.line}: forbidden `>` comparison; use `<` with operands flipped",
                                file=self.rel_path,
                                start_line=pos.start.line,
                                start_column=pos.start.column + 1,
                                end_line=pos.end.line,
                                end_column=pos.end.column + 1,
                                fix=FindingFix(
                                    description="flip operands and swap `>` to `<`",
                                    safety=FixSafety.UNSAFE,
                                    edits=(),
                                ),
                            )
                        )
                    elif isinstance(target.operator, cst.GreaterThanEqual):
                        pos = self.get_metadata(cst.metadata.PositionProvider, node)
                        self.findings.append(
                            Finding(
                                severity,
                                category,
                                f"{self.rel_path}:{pos.start.line}: forbidden `>=` comparison; use `<=` with operands flipped",
                                file=self.rel_path,
                                start_line=pos.start.line,
                                start_column=pos.start.column + 1,
                                end_line=pos.end.line,
                                end_column=pos.end.column + 1,
                                fix=FindingFix(
                                    description="flip operands and swap `>=` to `<=`",
                                    safety=FixSafety.UNSAFE,
                                    edits=(),
                                ),
                            )
                        )
                return True

        def collect_findings():
            for path in sources:
                tree, error = parse_python(path)
                if error is not None:
                    yield Finding(
                        severity,
                        self.category,
                        f"{relative(path, ctx.root)}: syntax error: {error}",
                    )
                    continue
                wrapper = cst.MetadataWrapper(tree)
                visitor = ComparisonFinder(relative(path, ctx.root))
                wrapper.visit(visitor)
                yield from visitor.findings

        return list(collect_findings())


RULE: HarnessCheckRule = GreaterThanComparisonRule()
