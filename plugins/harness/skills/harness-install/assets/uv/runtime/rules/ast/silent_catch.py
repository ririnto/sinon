#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Forbid silent catch rule.

Detects catch handlers that silently swallow exceptions:
- Empty catch body
- Pass-only catch body
- Unused exception parameter (name never referenced in body)
- Missing rethrow (raise)
- Missing configured logging call
"""

import re
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
        allowed_logging = self._resolve_allowed_logging(ctx)

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
                    pos = self.get_metadata(cst.metadata.PositionProvider, handler)
                    if not body_stmts:
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
                                continue
                    body_text = handler.body.visit(cst.RemovalSentinel.REMOVE) if False else ""
                    body_text = _body_text(handler)
                    param_name = _handler_param_name(handler)
                    has_raise = "raise" in body_text
                    has_logging = any(
                        re.search(pattern, body_text)
                        for pattern in allowed_logging
                    )
                    uses_param = param_name is not None and param_name in body_text
                    if not has_raise and not has_logging:
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
                                    description="add rethrow, structured logging, or error translation",
                                    safety=FixSafety.UNSAFE,
                                    edits=(),
                                ),
                            )
                        )
                    elif param_name is not None and not uses_param and not has_raise:
                        self.findings.append(
                            Finding(
                                severity,
                                category,
                                f"{self.rel_path}:{pos.start.line}: silent catch; exception parameter `{param_name}` is unused and handler does not rethrow",
                                file=self.rel_path,
                                start_line=pos.start.line,
                                start_column=pos.start.column + 1,
                                end_line=pos.end.line,
                                end_column=pos.end.column + 1,
                                fix=FindingFix(
                                    description="use the exception parameter or rethrow",
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

    def _resolve_allowed_logging(self, ctx: RuleContext) -> list[str]:
        """Resolve allowed logging call patterns from manifest parameters.

        Defaults to common Python logging patterns when not configured.
        """
        manifest = ctx.manifest.raw
        section = manifest.get(self.category, {})
        params = section.get("parameters", {})
        tokens = params.get("allowedLoggingCalls")
        if tokens:
            return list(tokens)
        return [
            r"\blogging\b",
            r"\blogger\b",
            r"\blog\b",
        ]


def _body_text(handler: cst.ExceptHandler) -> str:
    """Extract the text content of a handler body for pattern matching."""
    return str(handler.body)


def _handler_param_name(handler: cst.ExceptHandler) -> str | None:
    """Extract the exception parameter name from a catch handler, if named."""
    if handler.name is None:
        return None
    if isinstance(handler.name, cst.Name):
        return handler.name.value
    if isinstance(handler.name, cst.AsName):
        if isinstance(handler.name.name, cst.Name):
            return handler.name.name.value
    return None


RULE: HarnessCheckRule = SilentCatchRule()
