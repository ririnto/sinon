#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Forbid unchecked cast suppression rule.

Detects forbidden suppression tokens in:
- # type: ignore[...] comments
- # noqa: ... comments
"""

import sys
import re

from collections.abc import Iterable

import libcst as cst

from rules.harness_check_rule import (
    Finding,
    HarnessCheckRule,
    parse_python,
)
from core.rule_context import RuleContext, relative, stack_sources_configured

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class UncheckedCastSuppressionRule(HarnessCheckRule):
    """Validate uncheckedCastSuppression check."""

    category = "uncheckedCastSuppression"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        if not ctx.is_enabled(self.category):
            return False
        return stack_sources_configured(ctx.manifest.raw, self.category)

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate uncheckedCastSuppression check."""
        severity = ctx.severity_of(self.category)
        category = self.category
        sources = ctx.stack_sources(self.category)
        forbidden = self.resolve_forbidden_suppressions(ctx)
        allowed = self.resolve_allowed_suppressions(ctx)

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
                visitor = UncheckedCastSuppressionFinder(
                    relative(path, ctx.root), forbidden, allowed, severity, category
                )
                wrapper.visit(visitor)
                yield from visitor.findings

        return list(collect_findings())

    def resolve_forbidden_suppressions(self, ctx: RuleContext) -> set[str]:
        """
        Resolves forbiddenSuppressions from manifest parameters.

        Reads parameters.forbiddenSuppressions from the manifest section,
        defaulting to ["type: ignore"] when missing.
        """
        manifest = ctx.manifest.raw
        section = manifest.get(self.category, {})
        params = section.get("parameters", {})
        tokens = params.get("forbiddenSuppressions", ["type: ignore"])
        return set(tokens) if tokens else {"type: ignore"}

    def resolve_allowed_suppressions(self, ctx: RuleContext) -> set[str]:
        """Resolve allowedSuppressions from manifest parameters."""
        section = ctx.manifest.raw.get(self.category, {})
        params = section.get("parameters", {})
        tokens = params.get("allowedSuppressions", [])
        return set(tokens) if tokens else set()


class UncheckedCastSuppressionFinder(cst.CSTVisitor):
    """Find forbidden suppression tokens in comments."""

    METADATA_DEPENDENCIES = (cst.metadata.PositionProvider,)

    def __init__(
        self,
        rel_path: str,
        forbidden: set[str],
        allowed: set[str],
        severity: str,
        category: str,
    ) -> None:
        super().__init__()
        self.findings: list[Finding] = []
        self.rel_path = rel_path
        self.forbidden = forbidden
        self.allowed = allowed
        self.severity = severity
        self.category = category

    def visit_EmptyLine(self, node: cst.EmptyLine) -> bool:
        """Check comments for forbidden suppression tokens."""
        self.check_comment_line(node)
        return True

    def visit_SimpleStatementLine(self, node: cst.SimpleStatementLine) -> bool:
        """Check trailing comments on statements."""
        self.check_comment_line(node)
        return True

    def visit_IndentedBlock(self, node: cst.IndentedBlock) -> bool:
        """Check comments in indented blocks."""
        for stmt in node.body:
            if hasattr(stmt, "trailing_whitespace"):
                self.check_comment_line(stmt)
        return True

    def check_comment_line(self, node: cst.CSTNode) -> None:
        """Extract and check suppression tokens from comment lines."""
        if not hasattr(node, "leading_lines"):
            return
        for leading_line in node.leading_lines:
            if isinstance(leading_line, cst.EmptyLine):
                if leading_line.comment:
                    self.check_comment_text(leading_line.comment.value)
        if hasattr(node, "trailing_whitespace"):
            trailing = node.trailing_whitespace
            if hasattr(trailing, "comment") and trailing.comment:
                self.check_comment_text(trailing.comment.value)

    def check_comment_text(self, comment: str) -> None:
        """Check if comment contains forbidden suppression tokens."""
        if not comment.startswith("#"):
            return
        tokens = self.extract_suppression_tokens(comment)
        matching = tokens.intersection(self.forbidden - self.allowed)
        if matching:
            line_num = self.comment_line_prefix(comment)
            self.findings.append(
                Finding(
                    self.severity,
                    self.category,
                    f"{self.rel_path}:{line_num if line_num > 0 else '?'}: avoid suppression of forbidden tokens (`{comment}`); refactor to explicit handling",
                )
            )

    @staticmethod
    def comment_line_prefix(comment: str) -> int:
        """Extract a leading 1-indexed line number from a comment in ``<n>:rest`` form.

        :param comment: Comment text whose first line may begin with ``<digits>:``.
        :returns: The parsed integer or 0 when the prefix is missing or non-numeric.
        """
        lines = comment.split("\n")
        if not lines or ":" not in lines[0]:
            return 0
        prefix = lines[0].split(":")[0]
        if not prefix.isdigit():
            return 0
        return int(prefix)

    def extract_suppression_tokens(self, comment: str) -> set[str]:
        """
        Extract suppression tokens from:
        - # type: ignore[no-untyped-call]
        - # type: ignore
        - # noqa: F401
        - # noqa
        """
        tokens = set()
        if "type: ignore" in comment:
            tokens.add("type: ignore")
            match = re.search(r"type:\s*ignore\[([^\]]+)\]", comment)
            if match:
                codes = match.group(1).split(",")
                tokens.update(code.strip() for code in codes)
        if "noqa" in comment:
            tokens.add("noqa")
            match = re.search(r"noqa:\s*([A-Z0-9]+)", comment)
            if match:
                tokens.add(match.group(1))
        return tokens


RULE: HarnessCheckRule = UncheckedCastSuppressionRule()
