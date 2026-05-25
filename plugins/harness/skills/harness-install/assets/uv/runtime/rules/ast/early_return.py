#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Forbid early return rule."""

import sys

from collections.abc import Iterable

import libcst as cst

from rules.harness_check_rule import (
    Finding,
    FindingFix,
    FixSafety,
    HarnessCheckRule,
    has_nested_function,
    parse_python,
)
from core.rule_context import RuleContext, relative, stack_sources_configured

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class EarlyReturnRule(HarnessCheckRule):
    """Validate earlyReturn check."""

    category = "earlyReturn"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        if not ctx.is_enabled(self.category):
            return False
        return stack_sources_configured(ctx.manifest.raw, self.category)

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate earlyReturn check."""
        severity = ctx.severity_of(self.category)
        category = self.category
        sources = ctx.stack_sources(self.category)

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
                for stmt in node.body.body[:-1]:
                    if isinstance(stmt, cst.SimpleStatementLine):
                        for inner_stmt in stmt.body:
                            if isinstance(inner_stmt, cst.Return):
                                pos = self.get_metadata(
                                    cst.metadata.PositionProvider, stmt
                                )
                                self.findings.append(
                                    Finding(
                                        severity,
                                        category,
                                        f"{self.rel_path}:{pos.start.line}: function `{node.name.value}` has an early/mid return; restructure with single exit",
                                        file=self.rel_path,
                                        start_line=pos.start.line,
                                        start_column=pos.start.column + 1,
                                        end_line=pos.end.line,
                                        end_column=pos.end.column + 1,
                                        fix=FindingFix(
                                            description="rewrite with early return; review nested logic before applying",
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
                        category,
                        f"{relative(path, ctx.root)}: syntax error: {error}",
                    )
                    continue
                wrapper = cst.MetadataWrapper(tree)
                visitor = EarlyReturnFinder(relative(path, ctx.root))
                wrapper.visit(visitor)
                yield from visitor.findings
        return list(collect_findings())


RULE: HarnessCheckRule = EarlyReturnRule()
