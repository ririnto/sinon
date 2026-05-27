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


def resolve_visibility_tokens(ctx: RuleContext, category: str) -> tuple[str, ...]:
    """
    Resolve configured visibility tokens from the flat manifest shape.

    Reads ``parameters.visibility`` from the manifest section, defaulting to
    ``("module",)`` when missing. Returns an empty tuple when the configured
    list is present but empty.

    :param ctx: rule execution context carrying the manifest payload.
    :param category: manifest category key (e.g. ``"publicDeclarationDocComment"``).
    :returns: configured token tuple, or the per-stack default when missing.
    """
    section = ctx.manifest.raw.get(category)
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


def is_override_decorator(
    decorator: cst.Decorator, exempt_decorators: tuple[str, ...]
) -> bool:
    """Return whether a decorator matches a configured exemption token."""
    expression = decorator.decorator
    if isinstance(expression, cst.Name):
        return expression.value in exempt_decorators
    if isinstance(expression, cst.Attribute):
        return expression.attr.value in exempt_decorators
    return False


def resolve_exempt_decorators(ctx: RuleContext, category: str) -> tuple[str, ...]:
    """Resolve configured decorator names that exempt declarations from docs."""
    section = ctx.manifest.raw.get(category)
    params = section.get("parameters", {}) if isinstance(section, dict) else {}
    exempt_decorators = (
        params.get("exemptDecorators", ["override"])
        if isinstance(params, dict)
        else ["override"]
    )
    return tuple(item for item in exempt_decorators if isinstance(item, str))


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
        exempt_decorators = resolve_exempt_decorators(ctx, self.category)

        if not visibility_tokens:
            return []

        class DocCommentFinder(cst.CSTVisitor):
            METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)

            def __init__(self, rel_path: str) -> None:
                super().__init__()
                self.findings: list[Finding] = []
                self.rel_path = rel_path
                self.depth = 0

            def visit_FunctionDef(self, node: cst.FunctionDef) -> bool:
                self.depth += 1
                func_name = node.name.value
                if (
                    self.depth == 1
                    and not any(
                        is_override_decorator(decorator, exempt_decorators)
                        for decorator in node.decorators
                    )
                    and matches_visibility(func_name, visibility_tokens)
                ):
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

            def leave_FunctionDef(self, original_node: cst.FunctionDef) -> None:
                """Leave a function declaration."""
                self.depth -= 1

            def visit_ClassDef(self, node: cst.ClassDef) -> bool:
                self.depth += 1
                class_name = node.name.value
                if self.depth == 1 and matches_visibility(
                    class_name, visibility_tokens
                ):
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

            def leave_ClassDef(self, original_node: cst.ClassDef) -> None:
                """Leave a class declaration."""
                self.depth -= 1

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
