#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Require doc comment on public declaration rule.
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


def resolve_visibility_tokens(
    ctx: RuleContext, category: str
) -> tuple[str, ...]:
    """
    Resolve configured visibility tokens from the flat manifest shape.

    Reads ``parameters.visibility`` from the manifest section, defaulting to
    ``("module",)`` when missing. Returns an empty tuple when the configured
    list is present but empty.

    :param ctx: rule execution context carrying the manifest payload.
    :param category: manifest category key (e.g. ``"publicDeclarationDocComment"``).
    :returns: configured token tuple, or the per-stack default when missing.
    """
    manifest_dict = ctx.manifest.raw
    section = manifest_dict.get(category)
    if not isinstance(section, dict):
        return ("module",)
    params = section.get("parameters", {})
    if not isinstance(params, dict):
        return ("module",)
    tokens = params.get("visibility")
    if not isinstance(tokens, list):
        return ("module",)
    return tuple(t for t in tokens if isinstance(t, str))


def matches_visibility(name: str, tokens: tuple[str, ...]) -> bool:
    """
    Check if a declaration matches the configured visibility tokens.

    Supports "module" token: top-level declarations whose name does not start
    with underscore (PEP 8 module-level public convention).
    Unknown tokens are treated as no-op (no match).
    """
    for token in tokens:
        if token == "module" and not name.startswith("_"):
            return True
    return False


class PublicDeclarationDocCommentRule(HarnessCheckRule):
    """Validate publicDeclarationDocComment check."""

    category = "publicDeclarationDocComment"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        if not ctx.is_enabled(self.category):
            return False
        return stack_sources_configured(ctx.manifest.raw, self.category)

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate publicDeclarationDocComment check."""
        severity = ctx.severity_of(self.category)
        category = self.category
        sources = ctx.stack_sources(self.category)
        visibility_tokens = resolve_visibility_tokens(ctx, self.category)

        if not visibility_tokens:
            return []

        class DocCommentFinder(cst.CSTVisitor):
            METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)

            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path

            def visit_FunctionDef(self, node: cst.FunctionDef) -> bool:
                func_name = node.name.value
                if matches_visibility(func_name, visibility_tokens):
                    if not isinstance(node.body, cst.IndentedBlock):
                        return True
                    if not node.body.body:
                        return True
                    first_stmt = node.body.body[0]
                    has_docstring = False
                    if isinstance(first_stmt, cst.SimpleStatementLine):
                        if first_stmt.body and isinstance(first_stmt.body[0], cst.Expr):
                            expr_value = first_stmt.body[0].value
                            if isinstance(expr_value, cst.SimpleString) or isinstance(
                                expr_value, cst.ConcatenatedString
                            ):
                                has_docstring = True
                    if not has_docstring:
                        pos = self.get_metadata(cst.metadata.PositionProvider, node)
                        self.findings.append(
                            Finding(
                                severity,
                                category,
                                f"{self.rel_path}:{pos.start.line}: public declaration `{func_name}` is missing a documentation comment",
                                file=self.rel_path,
                                start_line=pos.start.line,
                                start_column=pos.start.column + 1,
                                end_line=pos.end.line,
                                end_column=pos.end.column + 1,
                                fix=FindingFix(
                                    description="add reStructuredText docstring with :param and :returns sections",
                                    safety=FixSafety.MANUAL,
                                    edits=(),
                                ),
                            )
                        )
                return True

            def visit_ClassDef(self, node: cst.ClassDef) -> bool:
                class_name = node.name.value
                if matches_visibility(class_name, visibility_tokens):
                    if not isinstance(node.body, cst.IndentedBlock):
                        return True
                    if not node.body.body:
                        return True
                    first_stmt = node.body.body[0]
                    has_docstring = False
                    if isinstance(first_stmt, cst.SimpleStatementLine):
                        if first_stmt.body and isinstance(first_stmt.body[0], cst.Expr):
                            expr_value = first_stmt.body[0].value
                            if isinstance(expr_value, cst.SimpleString) or isinstance(
                                expr_value, cst.ConcatenatedString
                            ):
                                has_docstring = True
                    if not has_docstring:
                        pos = self.get_metadata(cst.metadata.PositionProvider, node)
                        self.findings.append(
                            Finding(
                                severity,
                                category,
                                f"{self.rel_path}:{pos.start.line}: public declaration `{class_name}` is missing a documentation comment",
                                file=self.rel_path,
                                start_line=pos.start.line,
                                start_column=pos.start.column + 1,
                                end_line=pos.end.line,
                                end_column=pos.end.column + 1,
                                fix=FindingFix(
                                    description="add reStructuredText docstring with :param and :returns sections",
                                    safety=FixSafety.MANUAL,
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
                visitor = DocCommentFinder(relative(path, ctx.root))
                wrapper.visit(visitor)
                yield from visitor.findings
        return list(collect_findings())


RULE: HarnessCheckRule = PublicDeclarationDocCommentRule()
