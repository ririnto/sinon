#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.13"
# dependencies = ["libcst>=1.8.6"]
# ///
"""ABC for harness check rules with shared utility static methods."""

from __future__ import annotations

import json
import os
import stat
from abc import ABC, abstractmethod
from collections.abc import Iterable
from pathlib import Path
from typing import NamedTuple, TypeGuard

import libcst as cst

ROOT = Path.cwd()
JsonObject = dict[str, object]


class Finding(NamedTuple):
    """Represents a validation finding with severity, category, and message."""

    severity: str
    category: str
    message: str


class HarnessCheckRule(ABC):
    """Strategy ABC implemented by each validation rule."""

    @abstractmethod
    def applies(self, manifest: JsonObject) -> bool:
        """Check if this rule applies to the manifest."""

    @abstractmethod
    def validate(self, project_dir: Path, manifest: JsonObject) -> Iterable[Finding]:
        """Validate and return findings."""

    @staticmethod
    def is_relative_to(path: Path, root: Path) -> bool:
        """Return whether path stays within root."""
        try:
            path.relative_to(root)
            return True
        except ValueError:
            return False

    @staticmethod
    def read_text(path: Path) -> str:
        """Read file text, resolving allowed root contract symlinks."""
        resolved = HarnessCheckRule.allowed_root_contract_target(path) if path.is_symlink() else path
        if resolved is None:
            return ""
        try:
            return resolved.read_text(encoding="utf-8")
        except OSError:
            return ""

    @staticmethod
    def is_executable(path: Path) -> bool:
        """Check if file has executable bit, resolving allowed symlinks."""
        resolved = HarnessCheckRule.allowed_root_contract_target(path) if path.is_symlink() else path
        if resolved is None:
            return False
        try:
            return bool(resolved.stat().st_mode & stat.S_IXUSR)
        except OSError:
            return False

    @staticmethod
    def first_line(path: Path) -> str:
        """Get first line of file text."""
        lines = HarnessCheckRule.read_text(path).splitlines()
        return lines[0] if lines else ""

    @staticmethod
    def relative(path: Path) -> str:
        """Return path relative to ROOT or string representation."""
        try:
            return path.relative_to(ROOT).as_posix()
        except ValueError:
            return str(path)

    @staticmethod
    def allowed_root_contract_target(path: Path) -> Path | None:
        """Resolve root contract symlink (AGENTS.md <-> CLAUDE.md) if valid."""
        if path.parent != ROOT or path.name not in {"AGENTS.md", "CLAUDE.md"}:
            return None
        try:
            target_name = os.readlink(path)
        except OSError:
            return None
        expected = "CLAUDE.md" if path.name == "AGENTS.md" else "AGENTS.md"
        if target_name != expected:
            return None
        target = ROOT / target_name
        return target if target.parent == ROOT and not target.is_symlink() and target.is_file() else None

    @staticmethod
    def is_safe_file(path: Path) -> bool:
        """Check if path is a regular file or allowed root contract symlink."""
        return HarnessCheckRule.allowed_root_contract_target(path) is not None if path.is_symlink() else path.is_file()

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
            directories[:] = [name for name in directories if not (current_path / name).is_symlink()]
            output.extend(child for name in files if not (child := current_path / name).is_symlink())
        return tuple(output)

    @staticmethod
    def safe_file_or_walk(base: Path) -> tuple[Path, ...]:
        """Return single file (if safe) or walk directory; no unsafe symlinks."""
        if base.is_symlink() and HarnessCheckRule.allowed_root_contract_target(base) is None:
            return ()
        return (base,) if HarnessCheckRule.is_safe_file(base) else HarnessCheckRule.safe_walk(base)

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
        if path.is_symlink():
            return {}
        try:
            manifest = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return {}
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
    def stack_sources(root: Path, manifest: JsonObject, category: str) -> tuple[Path, ...]:
        """Resolve source files for a check category based on stack roots and extensions."""
        root = root.resolve()
        section = manifest.get(category, {})
        if not HarnessCheckRule.is_json_object(section):
            return ()
        params = section.get("parameters", {})
        if not HarnessCheckRule.is_json_object(params):
            return ()
        source_roots_per_stack = params.get("sourceRootsPerStack", {})
        if not HarnessCheckRule.is_json_object(source_roots_per_stack):
            return ()
        extensions_per_stack = params.get("extensionsPerStack", {})
        if not HarnessCheckRule.is_json_object(extensions_per_stack):
            return ()
        python_roots = source_roots_per_stack.get("python", [])
        if not isinstance(python_roots, list):
            return ()
        python_exts = extensions_per_stack.get("python", [])
        if not isinstance(python_exts, list):
            return ()
        ext_set = frozenset(e for e in python_exts if isinstance(e, str))
        seen = set()
        result = []
        for root_entry in python_roots:
            if not isinstance(root_entry, str):
                continue
            if "*" in root_entry:
                for resolved_path in root.glob(root_entry):
                    if (resolved_path.is_dir() and not resolved_path.is_symlink()
                            and HarnessCheckRule.is_relative_to(resolved_path.resolve(), root)):
                        for file_path in resolved_path.rglob("*"):
                            if (file_path.is_file() and not file_path.is_symlink()
                                    and "__pycache__" not in file_path.parts
                                    and (abs_path := file_path.resolve()) not in seen
                                    and HarnessCheckRule.is_relative_to(abs_path, root)
                                    and file_path.suffix.lstrip(".") in ext_set):
                                seen.add(abs_path)
                                result.append(abs_path)
            else:
                dir_path = root / root_entry
                if (dir_path.is_dir() and not dir_path.is_symlink()
                        and HarnessCheckRule.is_relative_to(dir_path.resolve(), root)):
                    for file_path in dir_path.rglob("*"):
                        if (file_path.is_file() and not file_path.is_symlink()
                                and "__pycache__" not in file_path.parts
                                and (abs_path := file_path.resolve()) not in seen
                                and HarnessCheckRule.is_relative_to(abs_path, root)
                                and file_path.suffix.lstrip(".") in ext_set):
                            seen.add(abs_path)
                            result.append(abs_path)
        return tuple(sorted(result))

    @staticmethod
    def parse_python(path: Path) -> tuple[cst.Module | None, str | None]:
        """Parse a Python file and return Module or error message."""
        try:
            return (cst.parse_module(path.read_text(encoding="utf-8")), None)
        except cst.ParserSyntaxError as err:
            return (None, str(err))

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
