"""Shared helpers for Sinon plugin package validation."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Protocol, TypeAlias, runtime_checkable


PLUGIN_SCHEMA = "https://json.schemastore.org/claude-code-plugin-manifest.json"
MARKETPLACE_SCHEMA = "https://json.schemastore.org/claude-code-marketplace.json"
KEBAB_RE = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
CLAUDE_AGENT_POINTER = "# CLAUDE.md\n\n@AGENTS.md\n"
JsonValue: TypeAlias = (
    None | bool | int | float | str | list["JsonValue"] | dict[str, "JsonValue"]
)


@runtime_checkable
class ReconfigurableTextStream(Protocol):
    """Text stream with Python's reconfigure method."""

    def reconfigure(self, *, encoding: str) -> None:
        """Reconfigure stream encoding."""


class Reporter:
    """Collect path-scoped validation errors."""

    def __init__(self, root: Path) -> None:
        self.root = root
        self.errors: list[str] = []

    def error(self, path: Path, message: str) -> None:
        """Append one repository-relative validation error."""
        relpath = path.relative_to(self.root)
        self.errors.append(f"{relpath}: {message}")


def configure_utf8_output() -> None:
    """Use UTF-8 for CLI output on hosts with non-UTF-8 defaults."""
    for stream in (sys.stdout, sys.stderr):
        if isinstance(stream, ReconfigurableTextStream):
            stream.reconfigure(encoding="utf-8")


def read_json(path: Path, reporter: Reporter) -> dict[str, JsonValue]:
    """Read a JSON file whose root must be an object."""
    try:
        value: JsonValue = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        reporter.error(path, f"invalid JSON: {exc.msg} at line {exc.lineno}")
        return {}
    if not isinstance(value, dict):
        reporter.error(path, "top-level JSON value must be an object")
        return {}
    return value


def parse_frontmatter(path: Path, reporter: Reporter) -> dict[str, str]:
    """Parse simple top-level YAML frontmatter scalar fields."""
    lines = path.read_text(encoding="utf-8").splitlines()
    if not lines or lines[0].strip() != "---":
        reporter.error(path, "missing YAML frontmatter")
        return {}
    end = None
    for index, line in enumerate(lines[1:], start=1):
        if line.strip() == "---":
            end = index
            break
    if end is None:
        reporter.error(path, "unterminated YAML frontmatter")
        return {}
    values: dict[str, str] = {}
    for line in lines[1:end]:
        if not line or line.startswith((" ", "\t", "-")):
            continue
        key, separator, value = line.partition(":")
        if separator:
            values[key.strip()] = value.strip().strip("\"'")
    return values


def skill_names(plugin_root: Path) -> list[str]:
    """Return packaged skill directory names."""
    skills_dir = plugin_root / "skills"
    if not skills_dir.is_dir():
        return []
    return sorted(
        child.name
        for child in skills_dir.iterdir()
        if child.is_dir() and (child / "SKILL.md").is_file()
    )


def extract_section(text: str, heading: str) -> str:
    """Extract an H2 section body by exact heading text."""
    lines = text.splitlines()
    start = None
    for index, line in enumerate(lines):
        if line.strip() == heading:
            start = index + 1
            break
    if start is None:
        return ""
    end = len(lines)
    for index in range(start, len(lines)):
        if lines[index].startswith("## "):
            end = index
            break
    return "\n".join(lines[start:end])


def table_first_column_names(section: str) -> set[str]:
    """Collect kebab-case names from the first column of a Markdown table."""
    names: set[str] = set()
    for line in section.splitlines():
        if not line.startswith("|"):
            continue
        cells = [cell.strip().strip("`") for cell in line.strip("|").split("|")]
        if not cells or cells[0].lower() == "skill" or set(cells[0]) <= {"-", " "}:
            continue
        if KEBAB_RE.fullmatch(cells[0]):
            names.add(cells[0])
    return names
