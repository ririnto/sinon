#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-

# /// script
# requires-python = ">=3.13"
# dependencies = ["libcst>=1.8.6"]
# ///
"""ABC for harness check rules with shared utility static methods."""

from __future__ import annotations

import json
import os
import stat
import sys
from abc import ABC, abstractmethod
from collections.abc import Iterable
from enum import Enum
from pathlib import Path
from typing import NamedTuple, TypeGuard

import libcst as cst

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

ROOT = Path.cwd()
JsonObject = dict[str, object]


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
    def applies(self, manifest: JsonObject) -> bool:
        """Check if this rule applies to the manifest."""

    @abstractmethod
    def validate(self, project_dir: Path, manifest: JsonObject) -> Iterable[Finding]:
        """Validate and return findings."""

    def format(self, ctx: object) -> Iterable[Path]:
        """
        Auto-format the project against this rule, when supported.

        Rules without an automatic fix MUST keep this default and return an
        empty iterable.

        :param ctx: rule execution context.
        :returns: iterable of absolute paths modified by this rule.
        """
        return ()

    @staticmethod
    def is_relative_to(path: Path, root: Path) -> bool:
        """Return whether path stays within root."""
        return path.is_relative_to(root)

    @staticmethod
    def read_text(path: Path) -> str:
        """Read file text, resolving allowed root contract symlinks."""
        resolved = (
            HarnessCheckRule.allowed_root_contract_target(path)
            if path.is_symlink()
            else path
        )
        if resolved is None or not resolved.is_file():
            return ""
        return resolved.read_text(encoding="utf-8")

    @staticmethod
    def is_executable(path: Path) -> bool:
        """Check if file has executable bit, resolving allowed symlinks."""
        resolved = (
            HarnessCheckRule.allowed_root_contract_target(path)
            if path.is_symlink()
            else path
        )
        if resolved is None or not resolved.exists():
            return False
        return bool(resolved.stat().st_mode & stat.S_IXUSR)

    @staticmethod
    def first_line(path: Path) -> str:
        """Get first line of file text."""
        lines = HarnessCheckRule.read_text(path).splitlines()
        return lines[0] if lines else ""

    @staticmethod
    def relative(path: Path) -> str:
        """Return path relative to ROOT or string representation."""
        if path.is_relative_to(ROOT):
            return path.relative_to(ROOT).as_posix()
        return str(path)

    @staticmethod
    def allowed_root_contract_target(path: Path) -> Path | None:
        """Resolve root contract symlink (AGENTS.md <-> CLAUDE.md) if valid."""
        if path.parent != ROOT or path.name not in {"AGENTS.md", "CLAUDE.md"}:
            return None
        if not path.is_symlink():
            return None
        target_name = os.readlink(path)
        if target_name != ("CLAUDE.md" if path.name == "AGENTS.md" else "AGENTS.md"):
            return None
        target = ROOT / target_name
        return (
            target
            if target.parent == ROOT and not target.is_symlink() and target.is_file()
            else None
        )

    @staticmethod
    def is_safe_file(path: Path) -> bool:
        """Check if path is a regular file or allowed root contract symlink."""
        return (
            HarnessCheckRule.allowed_root_contract_target(path) is not None
            if path.is_symlink()
            else path.is_file()
        )

    @staticmethod
    def is_safe_directory(path: Path) -> bool:
        """Check if path is a directory (not a symlink)."""
        return not path.is_symlink() and path.is_dir()

    @staticmethod
    def safe_walk(base: Path) -> tuple[Path, ...]:
        """Walk directory tree, excluding symlinks."""
        if base.is_symlink() or base.is_file() or not base.is_dir():
            return ()
        output = []
        for current, directories, files in os.walk(base, followlinks=False):
            current_path = Path(current)
            directories[:] = [
                name for name in directories if not (current_path / name).is_symlink()
            ]
            for child in (
                current_path / name
                for name in files
                if not (current_path / name).is_symlink()
            ):
                output.append(child)
        return tuple(output)

    @staticmethod
    def safe_file_or_walk(base: Path) -> tuple[Path, ...]:
        """Return single file (if safe) or walk directory; no unsafe symlinks."""
        if (
            base.is_symlink()
            and HarnessCheckRule.allowed_root_contract_target(base) is None
        ):
            return ()
        return (
            (base,)
            if HarnessCheckRule.is_safe_file(base)
            else HarnessCheckRule.safe_walk(base)
        )

    @staticmethod
    def is_json_object(value: object) -> TypeGuard[JsonObject]:
        """Return whether value is a JSON object shape used by manifests."""
        return isinstance(value, dict)

    @staticmethod
    def is_json_array(value: object) -> TypeGuard[list[object]]:
        """Return whether value is a JSON array shape used by manifests."""
        return isinstance(value, list)

    @staticmethod
    def json_array(value: object) -> list[object]:
        """Return list items from a JSON array value."""
        if HarnessCheckRule.is_json_array(value):
            return value
        return []

    @staticmethod
    def load_manifest(manifest_path: str = "docs/harness/manifest.json") -> JsonObject:
        """Load and parse manifest.json."""
        path = ROOT / manifest_path
        if path.is_symlink() or not path.is_file():
            return {}
        manifest = json.loads(path.read_text(encoding="utf-8"))
        return manifest if HarnessCheckRule.is_json_object(manifest) else {}

    @staticmethod
    def severity_for(manifest: JsonObject, category: str) -> str:
        """Get severity for category from manifest, default to ERROR."""
        section = manifest.get(category)
        if HarnessCheckRule.is_json_object(section):
            value = section.get("severity")
            if value in ("ERROR", "WARN", "INFO"):
                return value
        return "ERROR"

    @staticmethod
    def stack_sources(
        root: Path, manifest: JsonObject, category: str
    ) -> tuple[Path, ...]:
        """Resolve source files for a check category using flat parameter shape.

        :param root: Project root directory.
        :param manifest: Parsed manifest object.
        :param category: Category key.
        :returns: Tuple of absolute paths for the matching source files.
        """
        root = root.resolve()
        section = manifest.get(category, {})
        if not HarnessCheckRule.is_json_object(section):
            return ()
        params = section.get("parameters", {})
        if not HarnessCheckRule.is_json_object(params):
            return ()
        python_roots = params.get("sourceRoots", [])
        if not isinstance(python_roots, list):
            return ()
        python_exts = params.get("extensions", [])
        if not isinstance(python_exts, list):
            return ()
        def matches_filter(file_path: Path) -> bool:
            return (
                file_path.is_file()
                and not file_path.is_symlink()
                and "__pycache__" not in file_path.parts
                and HarnessCheckRule.is_relative_to(file_path.resolve(), root)
                and file_path.suffix.lstrip(".") in frozenset(e for e in python_exts if isinstance(e, str))
            )

        def collect_all() -> list[Path]:
            collected = []
            for root_entry in python_roots:
                if not isinstance(root_entry, str):
                    continue
                if "*" in root_entry:
                    for resolved_path in root.glob(root_entry):
                        if (
                            resolved_path.is_dir()
                            and not resolved_path.is_symlink()
                            and HarnessCheckRule.is_relative_to(
                                resolved_path.resolve(), root
                            )
                        ):
                            collected.extend(
                                file_path.resolve()
                                for file_path in resolved_path.rglob("*")
                                if matches_filter(file_path)
                            )
                else:
                    dir_path = root / root_entry
                    if (
                        dir_path.is_dir()
                        and not dir_path.is_symlink()
                        and HarnessCheckRule.is_relative_to(dir_path.resolve(), root)
                    ):
                        collected.extend(
                            file_path.resolve()
                            for file_path in dir_path.rglob("*")
                            if matches_filter(file_path)
                        )
            return collected

        return tuple(sorted(dict.fromkeys(collect_all())))

    @staticmethod
    def parse_python(path: Path) -> tuple[cst.Module | None, str | None]:
        """Parse a Python file and return Module or error message."""
        if not path.is_file():
            return (None, "file not found")
        return (cst.parse_module(path.read_text(encoding="utf-8")), None)

    @staticmethod
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
