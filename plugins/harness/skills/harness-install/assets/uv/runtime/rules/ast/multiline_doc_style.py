#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Require Python docstrings to use multiline style."""

from __future__ import annotations

import sys

from collections.abc import Iterable
from pathlib import Path

import libcst as cst

from core.rule_context import RuleContext, relative, stack_sources_configured
from rules.harness_check_rule import Finding, HarnessCheckRule, parse_python

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class MultilineDocStyleRule(HarnessCheckRule):
    """Validate multilineDocStyle check."""

    category = "multilineDocStyle"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        if not ctx.is_enabled(self.category):
            return False
        return stack_sources_configured(ctx.manifest.raw, self.category)

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate multilineDocStyle check."""
        if self.doc_style_mode(ctx) != "multiline":
            return []
        severity = ctx.severity_of(self.category)
        return [
            finding
            for path in ctx.stack_sources(self.category)
            for finding in self.validate_file(path, ctx, severity)
        ]

    def validate_file(self, path: Path, ctx: RuleContext, severity: str) -> tuple[Finding, ...]:
        """Validate one Python file."""
        tree, error = parse_python(path)
        if error is not None or tree is None:
            return (Finding(severity, self.category, f"{relative(path, ctx.root)}: syntax error: {error}"),)
        wrapper = cst.MetadataWrapper(tree)
        visitor = DocstringVisitor(ctx, path, severity)
        wrapper.visit(visitor)
        return tuple(visitor.findings)

    def doc_style_mode(self, ctx: RuleContext) -> str:
        """Return configured doc style mode."""
        section = ctx.manifest.raw.get(self.category, {})
        params = section.get("parameters", {}) if isinstance(section, dict) else {}
        mode = params.get("docStyleMode", "multiline") if isinstance(params, dict) else "multiline"
        return mode if isinstance(mode, str) else "multiline"


class DocstringVisitor(cst.CSTVisitor):
    """Collect single-line docstring findings."""

    METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)

    def __init__(self, ctx: RuleContext, path: Path, severity: str) -> None:
        """Initialize visitor state."""
        super().__init__()
        self.ctx = ctx
        self.path = path
        self.severity = severity
        self.findings: list[Finding] = []

    def visit_Module(self, node: cst.Module) -> bool:
        """Visit a module docstring."""
        self.record_docstring(node.body)
        return True

    def visit_FunctionDef(self, node: cst.FunctionDef) -> bool:
        """Visit a function docstring."""
        self.record_body(node.body)
        return True

    def visit_ClassDef(self, node: cst.ClassDef) -> bool:
        """Visit a class docstring."""
        self.record_body(node.body)
        return True

    def record_body(self, body: cst.BaseSuite) -> None:
        """Record a suite docstring if present."""
        if isinstance(body, cst.IndentedBlock):
            self.record_docstring(body.body)

    def record_docstring(self, body: cst.Sequence[cst.CSTNode]) -> None:
        """Record a docstring node if it spans a single line."""
        if not body:
            return
        first_stmt = body[0]
        if not isinstance(first_stmt, cst.SimpleStatementLine):
            return
        if not first_stmt.body or not isinstance(first_stmt.body[0], cst.Expr):
            return
        if not isinstance(first_stmt.body[0].value, cst.SimpleString):
            return
        pos = self.get_metadata(cst.metadata.PositionProvider, first_stmt.body[0])
        if pos.start.line == pos.end.line:
            rel_path = relative(self.path, self.ctx.root)
            self.findings.append(
                Finding(
                    self.severity,
                    "multilineDocStyle",
                    f"{rel_path}:{pos.start.line}: Python docstring must use multiline style",
                    file=rel_path,
                    start_line=pos.start.line,
                    start_column=pos.start.column + 1,
                    end_line=pos.end.line,
                    end_column=pos.end.column + 1,
                )
            )


RULE: HarnessCheckRule = MultilineDocStyleRule()
