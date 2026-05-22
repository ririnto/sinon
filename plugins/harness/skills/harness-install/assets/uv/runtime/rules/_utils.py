#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""Shared utilities for harness check rules."""

import os
import re
import stat
from pathlib import Path

import libcst as cst


ROOT = Path.cwd()


def read_text(path: Path) -> str:
    """Read file text, resolving allowed root contract symlinks."""
    resolved_path = path
    if path.is_symlink():
        target = allowed_root_contract_target(path)
        if target is not None:
            resolved_path = target
        else:
            resolved_path = None
    if resolved_path is None:
        result = ""
    else:
        try:
            result = resolved_path.read_text(encoding="utf-8")
        except OSError:
            result = ""
    return result


def is_executable(path: Path) -> bool:
    """Check if file has executable bit, resolving allowed symlinks."""
    resolved_path = path
    if path.is_symlink():
        target = allowed_root_contract_target(path)
        if target is not None:
            resolved_path = target
        else:
            resolved_path = None
    if resolved_path is None:
        result = False
    else:
        try:
            result = bool(resolved_path.stat().st_mode & stat.S_IXUSR)
        except OSError:
            result = False
    return result


def first_line(path: Path) -> str:
    """Get first line of file text."""
    lines = read_text(path).splitlines()
    result = lines[0] if lines else ""
    return result


def relative(path: Path) -> str:
    """Return path relative to ROOT or string representation."""
    try:
        result = path.relative_to(ROOT).as_posix()
    except ValueError:
        result = str(path)
    return result


def allowed_root_contract_target(path: Path) -> Path | None:
    """Resolve root contract symlink (AGENTS.md <-> CLAUDE.md) if valid."""
    result = None
    if path.parent == ROOT and path.name in {"AGENTS.md", "CLAUDE.md"}:
        try:
            target_name = os.readlink(path)
            expected = "CLAUDE.md" if path.name == "AGENTS.md" else "AGENTS.md"
            if target_name == expected:
                target = ROOT / target_name
                if target.parent == ROOT and not target.is_symlink() and target.is_file():
                    result = target
        except OSError:
            pass
    return result


def is_safe_file(path: Path) -> bool:
    """Check if path is a regular file or allowed root contract symlink."""
    if path.is_symlink():
        result = allowed_root_contract_target(path) is not None
    else:
        result = path.is_file()
    return result


def is_safe_directory(path: Path) -> bool:
    """Check if path is a directory (not a symlink)."""
    result = not path.is_symlink() and path.is_dir()
    return result


def safe_walk(base: Path) -> tuple[Path, ...]:
    """Walk directory tree, excluding symlinks."""
    if base.is_symlink() or base.is_file() or not base.is_dir():
        result = ()
    else:
        output = []
        for current, directories, files in os.walk(base, followlinks=False):
            current_path = Path(current)
            directories[:] = [name for name in directories if not (current_path / name).is_symlink()]
            output.extend(child for name in files if not (child := current_path / name).is_symlink())
        result = tuple(output)
    return result


def safe_file_or_walk(base: Path) -> tuple[Path, ...]:
    """Return single file (if safe) or walk directory; no unsafe symlinks."""
    if base.is_symlink() and allowed_root_contract_target(base) is None:
        result = ()
    elif is_safe_file(base):
        result = (base,)
    else:
        result = safe_walk(base)
    return result


def severity_for(manifest: dict, category: str) -> str:
    """Get severity for category from manifest, default to ERROR."""
    result = "ERROR"
    section = manifest.get(category)
    if isinstance(section, dict):
        value = section.get("severity")
        if value in ("ERROR", "WARN", "INFO"):
            result = value
    return result


def stack_sources(root: Path, manifest: dict, category: str) -> tuple[Path, ...]:
    """Resolve source files for a check category based on stack roots and extensions."""
    section = manifest.get(category, {})
    if not isinstance(section, dict):
        return ()
    params = section.get("parameters", {})
    if not isinstance(params, dict):
        return ()
    source_roots_per_stack = params.get("sourceRootsPerStack", {})
    if not isinstance(source_roots_per_stack, dict):
        return ()
    extensions_per_stack = params.get("extensionsPerStack", {})
    if not isinstance(extensions_per_stack, dict):
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
                if resolved_path.is_dir() and not resolved_path.is_symlink():
                    for file_path in resolved_path.rglob("*"):
                        if file_path.is_file() and not file_path.is_symlink():
                            if "__pycache__" not in file_path.parts:
                                suffix = file_path.suffix.lstrip(".")
                                if suffix in ext_set:
                                    abs_path = file_path.resolve()
                                    if abs_path not in seen:
                                        seen.add(abs_path)
                                        result.append(abs_path)
        else:
            dir_path = root / root_entry
            if dir_path.is_dir() and not dir_path.is_symlink():
                for file_path in dir_path.rglob("*"):
                    if file_path.is_file() and not file_path.is_symlink():
                        if "__pycache__" not in file_path.parts:
                            suffix = file_path.suffix.lstrip(".")
                            if suffix in ext_set:
                                abs_path = file_path.resolve()
                                if abs_path not in seen:
                                    seen.add(abs_path)
                                    result.append(abs_path)
    return tuple(sorted(result))


def parse_python(path: Path) -> tuple[cst.Module | None, str | None]:
    """Parse a Python file and return Module or error message."""
    text = path.read_text(encoding="utf-8")
    try:
        module = cst.parse_module(text)
        result = (module, None)
    except cst.ParserSyntaxError as err:
        result = (None, str(err))
    return result


def has_nested_function(func_node: cst.FunctionDef) -> bool:
    """Check if function body contains nested function, class, or lambda."""
    class _NestedFinder(cst.CSTVisitor):
        def __init__(self) -> None:
            super().__init__()
            self.found = False
            self._depth = 0
        def visit_FunctionDef(self, node: cst.FunctionDef) -> bool:
            if self._depth > 0:
                self.found = True
                return False
            self._depth += 1
            return True
        def visit_ClassDef(self, node: cst.ClassDef) -> bool:
            if self._depth > 0:
                self.found = True
                return False
            return True
        def leave_FunctionDef(self, original: cst.FunctionDef) -> None:
            self._depth -= 1
        def visit_Lambda(self, node: cst.Lambda) -> bool:
            self.found = True
            return False
    visitor = _NestedFinder()
    func_node.visit(visitor)
    return visitor.found
