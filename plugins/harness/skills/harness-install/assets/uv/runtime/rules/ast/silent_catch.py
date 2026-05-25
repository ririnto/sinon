#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Forbid silent catch rule.
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


class SilentCatchRule(HarnessCheckRule):
    """Validate silentCatch check."""

    category = "silentCatch"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        if not ctx.is_enabled(self.category):
            return False
        return stack_sources_configured(ctx.manifest.raw, self.category)

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate silentCatch check."""
        severity = ctx.severity_of(self.category)
        category = self.category
        sources = ctx.stack_sources(self.category)

        class SilentCatchFinder(cst.CSTVisitor):
            METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)

            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path

            def visit_Try(self, node: cst.Try) -> bool:
                for handler in node.handlers:
                    if not isinstance(handler.body, cst.IndentedBlock):
                        continue
                    body_stmts = handler.body.body
                    if not body_stmts:
                        pos = self.get_metadata(cst.metadata.PositionProvider, handler)
                        self.findings.append(
                            Finding(
                                severity,
                                category,
                                f"{self.rel_path}:{pos.start.line}: silent catch; rethrow, translate to a Finding, or log via structured logger",
                                file=self.rel_path,
                                start_line=pos.start.line,
                                start_column=pos.start.column + 1,
                                end_line=pos.end.line,
                                end_column=pos.end.column + 1,
                                fix=FindingFix(
                                    description="add exception handling: rethrow or log",
                                    safety=FixSafety.UNSAFE,
                                    edits=(),
                                ),
                            )
                        )
                        continue
                    if len(body_stmts) == 1:
                        stmt = body_stmts[0]
                        if isinstance(stmt, cst.SimpleStatementLine):
                            if len(stmt.body) == 1 and isinstance(
                                stmt.body[0], cst.Pass
                            ):
                                pos = self.get_metadata(
                                    cst.metadata.PositionProvider, handler
                                )
                                self.findings.append(
                                    Finding(
                                        severity,
                                        category,
                                        f"{self.rel_path}:{pos.start.line}: silent catch; rethrow, translate to a Finding, or log via structured logger",
                                        file=self.rel_path,
                                        start_line=pos.start.line,
                                        start_column=pos.start.column + 1,
                                        end_line=pos.end.line,
                                        end_column=pos.end.column + 1,
                                        fix=FindingFix(
                                            description="replace pass with exception handling",
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
                visitor = SilentCatchFinder(relative(path, ctx.root))
                wrapper.visit(visitor)
                yield from visitor.findings
        return list(collect_findings())


RULE: HarnessCheckRule = SilentCatchRule()
