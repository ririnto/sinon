#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-
"""
Shared context passed to all rule instances.
"""

from __future__ import annotations

import os
import stat
import sys

from fnmatch import fnmatch
from pathlib import Path
from typing import Protocol

from .manifest import (
    HarnessManifest,
    Manifest,
    as_record,
    create_manifest,
    is_enabled_from_manifest,
    severity_from_manifest,
)
from .severity import Severity

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")


class RuleContext(Protocol):
    """Shared context passed to all rule instances."""

    root: Path
    manifest: Manifest
    stack: str

    def is_enabled(
        self, category_or_manifest: str | HarnessManifest, category: str | None = None
    ) -> bool:
        """Check if category is enabled."""
        ...

    def severity_of(
        self, category_or_manifest: str | HarnessManifest, category: str | None = None
    ) -> Severity:
        """Get severity for category."""
        ...

    def string_array(self, value: object) -> list[str]:
        """Get string array from value."""
        ...

    def string_value(self, value: object) -> str:
        """Get string value from value."""
        ...

    def category_object(self, category: str) -> dict[str, object]:
        """Get category object."""
        ...

    def path_of(self, path: str) -> Path:
        """Get absolute path."""
        ...

    def read(self, path: str) -> str:
        """Read file content."""
        ...

    def first_line(self, path: str) -> str:
        """Get first line of file."""
        ...

    def is_file(self, path: str) -> bool:
        """Check if path is a file."""
        ...

    def is_directory(self, path: str) -> bool:
        """Check if path is a directory."""
        ...

    def is_executable(self, path: str) -> bool:
        """Check if file is executable."""
        ...

    def is_symlink(self, path: str) -> bool:
        """Check if path is a symlink."""
        ...

    def allowed_root_contract_target(self, path: str) -> Path | None:
        """Resolve allowed root contract symlink."""
        ...

    def read_string_array(self, value: object) -> list[str]:
        """Get string array from value."""
        ...

    def read_json_object(self, value: object) -> dict[str, object]:
        """Get JSON object from value."""
        ...

    def stack_sources(
        self,
        category: str,
    ) -> tuple[Path, ...]:
        """Collect source files for a stack."""
        ...

    def walk_directory(self, path: str) -> tuple[tuple[Path, ...], tuple]:
        """Walk directory tree."""
        ...

    def collect_files_under(self, path: str) -> tuple[tuple[Path, ...], tuple]:
        """Collect files under path."""
        ...


def relative(path: Path, root: Path) -> str:
    """
    Return path relative to root or string representation.

    :param path: Path to convert.
    :param root: Root path for relative conversion.
    :returns: Relative POSIX path string when path is contained in root, otherwise the absolute path string.
    :rtype: str
    """
    if path.is_relative_to(root):
        return path.relative_to(root).as_posix()
    return str(path)


def stack_sources_configured(raw_manifest: dict[str, object], category: str) -> bool:
    """
    Check whether the given category declares a non-empty flat ``sourceRoots``
    list.

    :param raw_manifest: Parsed manifest object.
    :param category: Category key under the manifest root.
    :returns: ``True`` when ``parameters.sourceRoots`` is a non-empty list.
    """
    section = raw_manifest.get(category)
    if not isinstance(section, dict):
        return False
    params = section.get("parameters", {})
    if not isinstance(params, dict):
        return False
    source_roots = params.get("sourceRoots")
    if not isinstance(source_roots, list):
        return False
    return len(source_roots) > 0


def create_rule_context(
    root_directory: Path,
    raw_manifest: object,
    stack: str = "python",
) -> RuleContext:
    """Factory function to create a RuleContext for a given root directory and manifest."""
    root_dir = root_directory.resolve()
    manifest = create_manifest(raw_manifest)

    def path_of(path: str) -> Path:
        return root_dir / path

    def is_within_root(path_str: str) -> bool:
        resolved_path = path_of(path_str).resolve()
        return resolved_path == root_dir or str(resolved_path).startswith(
            f"{root_dir}/"
        )

    def read(path_str: str) -> str:
        target = allowed_root_contract_target(path_str)
        file_path = target if target is not None else path_of(path_str)
        if not file_path.is_file():
            return ""
        return file_path.read_text(encoding="utf-8")

    def first_line(path_str: str) -> str:
        lines = read(path_str).splitlines()
        return lines[0] if lines else ""

    def is_file(path_str: str) -> bool:
        if is_symlink(path_str) and allowed_root_contract_target(path_str) is None:
            return False
        return path_of(path_str).is_file()

    def is_directory(path_str: str) -> bool:
        if is_symlink(path_str):
            return False
        return path_of(path_str).is_dir()

    def is_executable(path_str: str) -> bool:
        target = allowed_root_contract_target(path_str)
        target_path = target if target is not None else path_of(path_str)
        if not target_path.exists():
            return False
        return bool(target_path.stat().st_mode & stat.S_IXUSR)

    def is_symlink(path_str: str) -> bool:
        return path_of(path_str).is_symlink()

    def allowed_root_contract_target(path_str: str) -> Path | None:
        path = path_of(path_str)
        if path.parent != root_dir or path.name not in {"AGENTS.md", "CLAUDE.md"}:
            return None
        if not path.is_symlink():
            return None
        target_name = os.readlink(path)
        if target_name != ("CLAUDE.md" if path.name == "AGENTS.md" else "AGENTS.md"):
            return None
        target = root_dir / target_name
        if target.parent == root_dir and not target.is_symlink() and target.is_file():
            return target
        return None

    def read_string_array(value: object) -> list[str]:
        if isinstance(value, list):
            return [item for item in value if isinstance(item, str)]
        return []

    def read_json_object(value: object) -> dict[str, object]:
        if isinstance(value, dict):
            return value
        return {}

    def stack_sources(
        category: str,
    ) -> tuple[Path, ...]:
        """
        Collect source files declared by the flat ``sourceRoots`` list.

        :param category: Category key under the manifest root.
        :returns: Tuple of source file paths.
        """
        section = manifest.raw.get(category, {})
        if not isinstance(section, dict):
            return ()
        params = section.get("parameters", {})
        if not isinstance(params, dict):
            return ()
        source_roots = params.get("sourceRoots", [])
        if not isinstance(source_roots, list):
            return ()
        exts = params.get("extensions", [])
        if not isinstance(exts, list):
            return ()
        include_globs = params.get("includePaths", [])
        if not isinstance(include_globs, list):
            include_globs = []
        exclude_globs = params.get("excludePaths", [])
        if not isinstance(exclude_globs, list):
            exclude_globs = []
        ext_set = frozenset(e for e in exts if isinstance(e, str))
        include_glob_list = [g for g in include_globs if isinstance(g, str)]
        exclude_glob_list = [g for g in exclude_globs if isinstance(g, str)]

        def collect_all() -> list[Path]:
            collected = []
            for root_entry in source_roots:
                if not isinstance(root_entry, str):
                    continue
                if "*" in root_entry:
                    for resolved_path in root_dir.glob(root_entry):
                        if (
                            resolved_path.is_dir()
                            and not resolved_path.is_symlink()
                            and resolved_path.is_relative_to(root_dir)
                        ):
                            collected.extend(
                                file_path
                                for file_path in resolved_path.rglob("*")
                                if (
                                    file_path.is_file()
                                    and not file_path.is_symlink()
                                    and "__pycache__" not in file_path.parts
                                    and file_path.is_relative_to(root_dir)
                                    and file_path.suffix.lstrip(".") in ext_set
                                )
                            )
                else:
                    dir_path = root_dir / root_entry
                    if (
                        dir_path.is_dir()
                        and not dir_path.is_symlink()
                        and dir_path.is_relative_to(root_dir)
                    ):
                        collected.extend(
                            file_path
                            for file_path in dir_path.rglob("*")
                            if (
                                file_path.is_file()
                                and not file_path.is_symlink()
                                and "__pycache__" not in file_path.parts
                                and file_path.is_relative_to(root_dir)
                                and file_path.suffix.lstrip(".") in ext_set
                            )
                        )
            return collected

        deduped = list(dict.fromkeys(collect_all()))
        included = [
            file_path
            for file_path in deduped
            if not include_glob_list
            or any(
                fnmatch(file_path.relative_to(root_dir).as_posix(), glob_pattern)
                for glob_pattern in include_glob_list
            )
        ]
        excluded = [
            file_path
            for file_path in included
            if not exclude_glob_list
            or not any(
                fnmatch(file_path.relative_to(root_dir).as_posix(), glob_pattern)
                for glob_pattern in exclude_glob_list
            )
        ]
        return tuple(sorted(excluded))

    def walk_directory(path_str: str) -> tuple[tuple[Path, ...], tuple]:
        """Walk directory tree, excluding symlinks."""
        path = path_of(path_str)
        if path.is_symlink() or path.is_file() or not path.is_dir():
            return ((), ())
        output: list[Path] = []
        for current, directories, files in os.walk(path, followlinks=False):
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
        return (tuple(output), ())

    def collect_files_under(path_str: str) -> tuple[tuple[Path, ...], tuple]:
        """Collect files under path."""
        if not is_within_root(path_str):
            return ((), ())
        if is_symlink(path_str) and allowed_root_contract_target(path_str) is None:
            return ((), ())
        if is_file(path_str):
            return ((path_of(path_str),), ())
        if is_directory(path_str):
            return walk_directory(path_str)
        return ((), ())

    class RuleContextImpl:
        """RuleContext implementation."""

        def __init__(self) -> None:
            """Initialize context."""
            self.root = root_dir
            self.manifest = manifest
            self.stack = stack

        def is_enabled(
            self,
            category_or_manifest: str | HarnessManifest,
            category: str | None = None,
        ) -> bool:
            """Check if category is enabled."""
            if isinstance(category_or_manifest, str):
                return self.manifest.is_enabled(category_or_manifest)
            if isinstance(category, str):
                return is_enabled_from_manifest(
                    as_record(category_or_manifest), category
                )
            return True

        def severity_of(
            self,
            category_or_manifest: str | HarnessManifest,
            category: str | None = None,
        ) -> Severity:
            """Get severity for category."""
            if isinstance(category_or_manifest, str):
                return self.manifest.severity_of(category_or_manifest)
            if isinstance(category, str):
                return severity_from_manifest(as_record(category_or_manifest), category)
            return "ERROR"

        def string_array(self, value: object) -> list[str]:
            """Get string array."""
            return read_string_array(value)

        def string_value(self, value: object) -> str:
            """Get string value."""
            if isinstance(value, str):
                return value
            return ""

        def category_object(self, category: str) -> dict[str, object]:
            """Get category object."""
            return self.manifest.category_object(category)

        def path_of(self, path_str: str) -> Path:
            """Get absolute path."""
            return path_of(path_str)

        def read(self, path_str: str) -> str:
            """Read file content."""
            return read(path_str)

        def first_line(self, path_str: str) -> str:
            """Get first line."""
            return first_line(path_str)

        def is_file(self, path_str: str) -> bool:
            """Check if file."""
            return is_file(path_str)

        def is_directory(self, path_str: str) -> bool:
            """Check if directory."""
            return is_directory(path_str)

        def is_executable(self, path_str: str) -> bool:
            """Check if executable."""
            return is_executable(path_str)

        def is_symlink(self, path_str: str) -> bool:
            """Check if symlink."""
            return is_symlink(path_str)

        def allowed_root_contract_target(self, path_str: str) -> Path | None:
            """Get allowed contract target."""
            return allowed_root_contract_target(path_str)

        def read_string_array(self, value: object) -> list[str]:
            """Get string array."""
            return read_string_array(value)

        def read_json_object(self, value: object) -> dict[str, object]:
            """Get JSON object."""
            return read_json_object(value)

        def stack_sources(
            self,
            category: str,
        ) -> tuple[Path, ...]:
            """Get stack sources."""
            return stack_sources(category)

        def walk_directory(self, path_str: str) -> tuple[tuple[Path, ...], tuple]:
            """Walk directory."""
            return walk_directory(path_str)

        def collect_files_under(self, path_str: str) -> tuple[tuple[Path, ...], tuple]:
            """Collect files under path."""
            return collect_files_under(path_str)

    return RuleContextImpl()
