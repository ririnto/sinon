#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Forbid same-line comments on triple-double-quoted strings.
"""

from __future__ import annotations

import sys


import io
import tokenize
from collections.abc import Iterable
from pathlib import Path

from rules.harness_check_rule import Finding, HarnessCheckRule
from core.rule_context import RuleContext, relative, stack_sources_configured

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class TripleQuoteInlineCommentRule(HarnessCheckRule):
    """Validate tripleQuoteInlineComment check."""

    category = "tripleQuoteInlineComment"

    def applies(self, ctx: RuleContext) -> bool:
        """Check if this rule applies to the context."""
        if not ctx.is_enabled(self.category):
            return False
        return stack_sources_configured(ctx.manifest.raw, self.category)

    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """Validate tripleQuoteInlineComment check."""
        severity = ctx.severity_of(self.category)
        return [
            finding
            for path in ctx.stack_sources(self.category)
            for finding in self.find_same_line_comments(path, ctx, severity)
        ]

    def find_same_line_comments(
        self, path: Path, ctx: RuleContext, severity: str
    ) -> list[Finding]:
        """Find comments sharing a physical line with triple-double-quoted strings."""
        string_lines: set[int] = set()
        comment_lines: set[int] = set()
        tokens = tokenize.generate_tokens(io.StringIO(ctx.read(str(path))).readline)
        for token_info in tokens:
            if self.is_triple_double_string_token(token_info):
                string_lines.add(token_info.start[0])
                string_lines.add(token_info.end[0])
            elif token_info.type == tokenize.COMMENT:
                comment_lines.add(token_info.start[0])
        return [
            Finding(
                severity,
                self.category,
                f"{relative(path, ctx.root)}:{line}: comment on same line as triple-double-quoted string",
            )
            for line in sorted(string_lines & comment_lines)
        ]

    @staticmethod
    def is_triple_double_string_token(token_info: tokenize.TokenInfo) -> bool:
        """Return whether a token represents triple-double-quoted string syntax."""
        if token_info.type == tokenize.STRING:
            return TripleQuoteInlineCommentRule.uses_triple_double_quote(
                token_info.string
            )
        fstring_start = getattr(tokenize, "FSTRING_START", None)
        fstring_end = getattr(tokenize, "FSTRING_END", None)
        return token_info.type in {
            fstring_start,
            fstring_end,
        } and token_info.string.startswith('"""')

    @staticmethod
    def uses_triple_double_quote(token_string: str) -> bool:
        """Return whether a STRING token is delimited by triple double quotes."""
        index = 0
        while index < len(token_string) and token_string[index] in "rRuUbBfFtT":
            index += 1
        return token_string.startswith('"""', index)


RULE: HarnessCheckRule = TripleQuoteInlineCommentRule()
