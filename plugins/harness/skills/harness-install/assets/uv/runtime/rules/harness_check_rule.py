#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
ABC for harness check rules.
"""

from __future__ import annotations

import sys


from abc import ABC, abstractmethod
from collections.abc import Iterable
from enum import Enum
from pathlib import Path
from typing import NamedTuple

import libcst as cst

from core.rule_context import RuleContext

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class FixSafety(str, Enum):
    """
    Indicates how safely an auto-fix can be applied.

    :cvar SAFE: Fix preserves program semantics (e.g., simple formatting).
    :cvar UNSAFE: Fix may alter program behavior; requires review.
    :cvar MANUAL: No automatic fix is available; manual intervention required.
    """

    SAFE = "safe"
    UNSAFE = "unsafe"
    MANUAL = "manual"


class FindingEdit(NamedTuple):
    """
    A concrete text edit that a fix would apply.

    :ivar file: Repository-relative POSIX path of the file to edit.
    :ivar start_line: 1-indexed start line.
    :ivar start_column: 1-indexed start column.
    :ivar end_line: 1-indexed end line (inclusive).
    :ivar end_column: 1-indexed end column (exclusive).
    :ivar replacement: Text that replaces the selected range.
    """

    file: str
    start_line: int
    start_column: int
    end_line: int
    end_column: int
    replacement: str


class FindingFix(NamedTuple):
    """
    Describes how a rule violation would be fixed.

    :ivar description: One-line human-readable description of the fix.
    :ivar safety: Safety classification of the fix.
    :ivar edits: Optional list of concrete edits; empty when the rule cannot
        provide exact text spans.
    """

    description: str
    safety: FixSafety
    edits: tuple[FindingEdit, ...] = ()


class Finding(NamedTuple):
    """
    A validation finding emitted by a rule.

    :ivar severity: Severity level (ERROR/WARN/INFO).
    :ivar category: Manifest category key.
    :ivar message: Human-readable violation message.
    :ivar file: Repository-relative POSIX path of the offending file (optional).
    :ivar start_line: 1-indexed start line (optional).
    :ivar start_column: 1-indexed start column (optional).
    :ivar end_line: 1-indexed end line (optional).
    :ivar end_column: 1-indexed end column (optional).
    :ivar fix: Fix metadata, when an automatic or assistive fix is known.
    """

    severity: str
    category: str
    message: str
    file: str | None = None
    start_line: int | None = None
    start_column: int | None = None
    end_line: int | None = None
    end_column: int | None = None
    fix: FindingFix | None = None


class HarnessCheckRule(ABC):
    """Strategy ABC implemented by each validation rule."""

    @abstractmethod
    def applies(self, ctx: RuleContext) -> bool:
        """
        Check if this rule applies to the context.

        :param ctx: rule execution context.
        :returns: ``True`` when the rule should run against ``ctx``.
        """

    @abstractmethod
    def validate(self, ctx: RuleContext) -> Iterable[Finding]:
        """
        Validate the project and return findings.

        :param ctx: rule execution context.
        :returns: iterable of findings; empty when no issues are found.
        """

    def format(self, ctx: RuleContext) -> Iterable[Path]:
        """
        Auto-format the project against this rule, when supported.

        Rules without an automatic fix MUST keep this default and return an
        empty iterable.

        :param ctx: rule execution context.
        :returns: iterable of absolute paths modified by this rule.
        """
        return ()


def parse_python(path: Path) -> tuple[cst.Module | None, str | None]:
    """Parse a Python file and return Module or error message."""
    if not path.is_file():
        return (None, "file not found")
    module = cst.parse_module(path.read_text(encoding="utf-8"))
    return (module, None)


def has_nested_function(func_node: cst.FunctionDef) -> bool:
    """Check if function body contains nested function, class, or lambda."""

    class NestedFinder(cst.CSTVisitor):
        def __init__(self) -> None:
            super().__init__()
            self.found = False
            self.depth = 0

        def visit_FunctionDef(self, node: cst.FunctionDef) -> bool:
            if self.depth > 0:
                self.found = True
                return False
            self.depth += 1
            return True

        def visit_ClassDef(self, node: cst.ClassDef) -> bool:
            if self.depth > 0:
                self.found = True
                return False
            return True

        def leave_FunctionDef(self, original: cst.FunctionDef) -> None:
            self.depth -= 1

        def visit_Lambda(self, node: cst.Lambda) -> bool:
            self.found = True
            return False

    visitor = NestedFinder()
    func_node.visit(visitor)
    return visitor.found
