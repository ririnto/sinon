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
                                    "add exception handling: rethrow or log",
                                    FixSafety.UNSAFE,
                                    (),
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
                                            "replace pass with exception handling",
                                            FixSafety.UNSAFE,
                                            (),
                                        ),
                                    )
                                )
                                continue
                    param_name = _handler_param_name(handler)
                    inspector = _HandlerBodyInspector(param_name, allowed_logging)
                    handler.body.visit(inspector)
                    has_raise = inspector.has_raise
                    if not has_raise and not inspector.has_logging_call:
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
                                    "add rethrow, structured logging, or error translation",
                                    FixSafety.UNSAFE,
                                    (),
                                ),
                            )
                        )
                    elif param_name is not None and not inspector.uses_param and not has_raise:
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
                                    "use the exception parameter or rethrow",
                                    FixSafety.UNSAFE,
                                    (),
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

        return tuple(collect_findings())

    def _resolve_allowed_logging(self, ctx: RuleContext) -> list[str]:
        """Resolve allowed logging call patterns from manifest parameters.

        Defaults to common Python logging patterns when not configured.
        """
        section = ctx.manifest.raw.get(self.category, {})
        params = section.get("parameters", {})
        tokens = params.get("allowedLoggingCalls")
        return (
            [_logging_token_name(token) for token in tokens]
            if tokens
            else ["logging", "logger", "log"]
        )


class _HandlerBodyInspector(cst.CSTVisitor):
    """Inspect a handler body structurally for handling signals."""

    def __init__(self, param_name: str | None, allowed_logging: list[str]) -> None:
        self.param_name = param_name
        self.allowed_logging = set(allowed_logging)
        self.has_raise = False
        self.has_logging_call = False
        self.uses_param = False

    def visit_Raise(self, node: cst.Raise) -> bool:
        """Record an actual raise statement."""
        self.has_raise = True
        return False

    def visit_Name(self, node: cst.Name) -> None:
        """Record an actual reference to the exception parameter."""
        if self.param_name is not None and node.value == self.param_name:
            self.uses_param = True

    def visit_Call(self, node: cst.Call) -> bool:
        """Record configured logging calls without scanning source text."""
        call_name = _call_name(node.func)
        if call_name is not None and _matches_logging_call(call_name, self.allowed_logging):
            self.has_logging_call = True
        return True


def _logging_token_name(token: str) -> str:
    """Convert common word-boundary logging patterns to identifier tokens."""
    return token.replace(r"\b", "").replace("\\", "")


def _matches_logging_call(call_name: str, allowed_logging: set[str]) -> bool:
    """Check a dotted call name against configured logging identifiers."""
    parts = call_name.split(".")
    return any(token in parts or call_name == token or call_name.startswith(f"{token}.") for token in allowed_logging)


def _call_name(node: cst.BaseExpression) -> str | None:
    """Return the dotted call target name for Name and Attribute calls."""
    if isinstance(node, cst.Name):
        return node.value
    if isinstance(node, cst.Attribute):
        parent_name = _call_name(node.value)
        return f"{parent_name}.{node.attr.value}" if parent_name is not None else node.attr.value
    return None


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
