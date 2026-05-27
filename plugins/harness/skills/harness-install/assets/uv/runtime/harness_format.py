#!/usr/bin/env -S uv run
# -*- coding: utf-8 -*-

# /// script
# requires-python = ">=3.13"
# dependencies = ["libcst>=1.8.6"]
# ///

"""
Format harness files by applying safe validate() fixes.
"""

from __future__ import annotations

import json
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

from core.rule_context import RuleContext, create_rule_context
from harness_check import HarnessCheck, MANIFEST_PATH
from harness_check_rule import Finding, FindingEdit, FixSafety

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")

FORMAT_ALLOWLIST = frozenset(
    {
        "leafFunctionBlankLines",
        "emptyDirectoryPlaceholders",
        "envShebangUsage",
        "hookGeneratedMarker",
        "hookShebang",
        "shebangEncodingMarker",
    }
)


@dataclass(frozen=True)
class PreparedEdit:
    """
    Text edit with validated byte-independent character offsets.

    :ivar edit: Original finding edit.
    :ivar relative_file: Repository-relative POSIX file path.
    :ivar start_offset: 0-indexed start character offset.
    :ivar end_offset: 0-indexed exclusive end character offset.
    """

    edit: FindingEdit
    relative_file: str
    start_offset: int
    end_offset: int


def relative_file_of(file: str, root: Path) -> str | None:
    """
    Convert an edit path to a repository-relative POSIX path.

    :param file: Finding edit file path.
    :param root: Repository root path.
    :returns: Repository-relative path, or None when outside root.
    """
    resolved_file = (Path(file) if Path(file).is_absolute() else root / file).resolve(strict=False)
    resolved_root = root.resolve(strict=False)
    if resolved_file == resolved_root or not resolved_file.is_relative_to(
        resolved_root
    ):
        return None
    return resolved_file.relative_to(resolved_root).as_posix()


def absolute_file_of(relative_file: str, root: Path) -> Path:
    """
    Resolve a repository-relative path under the root.

    :param relative_file: Repository-relative POSIX file path.
    :param root: Repository root path.
    :returns: Absolute path under root.
    """
    return root / relative_file


def is_generated_artifact(ctx: RuleContext, relative_file: str, category: str) -> bool:
    """
    Check whether a file is under the generated artifact directory.

    :param ctx: Rule execution context.
    :param relative_file: Repository-relative POSIX file path.
    :param category: Finding category.
    :returns: True when the file is generated and should not be edited.
    """
    if category == "emptyDirectoryPlaceholders":
        return False
    generated_artifacts = ctx.read_json_object(
        ctx.manifest.raw.get("generatedArtifacts")
    )
    generated_path = generated_artifacts.get("path")
    if not isinstance(generated_path, str) or generated_path == "":
        return False
    return relative_file != generated_artifacts.get("placeholder") and relative_file.startswith(
        generated_path if generated_path.endswith("/") else f"{generated_path}/"
    )


def editable_file(ctx: RuleContext, relative_file: str, category: str) -> bool:
    """
    Check whether a file is safe for formatter writes.

    :param ctx: Rule execution context.
    :param relative_file: Repository-relative POSIX file path.
    :param category: Finding category.
    :returns: True when formatter may edit or create the file.
    """
    if is_generated_artifact(ctx, relative_file, category):
        return False
    if (
        ctx.is_symlink(relative_file)
        and ctx.allowed_root_contract_target(relative_file) is None
    ):
        return False
    if category == "emptyDirectoryPlaceholders" and relative_file.endswith("/.gitkeep"):
        return True
    return ctx.is_file(relative_file)


def line_starts_of(text: str) -> list[int]:
    """
    Compute 0-indexed line start offsets.

    :param text: File text.
    :returns: Start offsets for every addressable line.
    """
    return [0] + [
        index + 1 for index, character in enumerate(text) if character == "\n"
    ]


def line_length_at(text: str, line_start: int) -> int:
    """
    Compute line length excluding a trailing carriage return.

    :param text: File text.
    :param line_start: 0-indexed line start offset.
    :returns: Line length before newline.
    """
    newline_index = text.find("\n", line_start)
    return len(text[line_start:(len(text) if newline_index == -1 else newline_index)].removesuffix("\r"))


def offset_of(text: str, line_starts: list[int], line: int, column: int) -> int:
    """
    Convert 1-indexed line and column to a 0-indexed character offset.

    :param text: File text.
    :param line_starts: Line start offsets from line_starts_of().
    :param line: 1-indexed line number.
    :param column: 1-indexed column number.
    :returns: 0-indexed character offset.
    :raises ValueError: If the coordinate is outside the file.
    """
    if line < 1 or line > len(line_starts):
        raise ValueError(f"invalid edit line {line}")
    line_start = line_starts[line - 1]
    if column < 1 or column > line_length_at(text, line_start) + 1:
        raise ValueError(f"invalid edit column {line}:{column}")
    return line_start + column - 1


def prepare_edit(text: str, edit: FindingEdit, relative_file: str) -> PreparedEdit:
    """
    Validate an edit range against file text.

    :param text: Current file text.
    :param edit: Finding edit to prepare.
    :param relative_file: Repository-relative POSIX file path.
    :returns: Prepared edit with character offsets.
    :raises ValueError: If the edit range is invalid.
    """
    line_starts = line_starts_of(text)
    start_offset = offset_of(text, line_starts, edit.start_line, edit.start_column)
    end_offset = offset_of(text, line_starts, edit.end_line, edit.end_column)
    if end_offset < start_offset:
        raise ValueError(
            f"edit range is reversed: {relative_file}:{edit.start_line}:{edit.start_column}"
        )
    return PreparedEdit(edit, relative_file, start_offset, end_offset)


def edits_for_finding(finding: Finding) -> tuple[FindingEdit, ...]:
    """
    Get safe formatter edits from a finding.

    :param finding: Validation finding.
    :returns: Tuple of edits to apply.
    """
    if finding.fix is not None and finding.fix.edits:
        return finding.fix.edits
    if finding.category == "emptyDirectoryPlaceholders" and finding.file is not None:
        return (
            FindingEdit(
                file=f"{finding.file}/.gitkeep",
                start_line=1,
                start_column=1,
                end_line=1,
                end_column=1,
                replacement="",
            ),
        )
    return ()


def collect_findings(ctx: RuleContext) -> tuple[Finding, ...]:
    """
    Run all applicable rule validators.

    :param ctx: Rule execution context.
    :returns: Tuple of validation findings.
    """
    return tuple(
        finding
        for check in HarnessCheck
        if check.rule.applies(ctx)
        for finding in check.rule.validate(ctx)
    )


def collect_safe_edits(
    ctx: RuleContext, findings: tuple[Finding, ...]
) -> dict[str, list[PreparedEdit]]:
    """
    Collect allowlisted SAFE edits grouped by file.

    :param ctx: Rule execution context.
    :param findings: Validation findings.
    :returns: Mapping of repository-relative file paths to prepared edits.
    """
    by_file: dict[str, list[PreparedEdit]] = defaultdict(list)
    for finding in findings:
        if finding.category not in FORMAT_ALLOWLIST:
            continue
        if finding.fix is None or finding.fix.safety != FixSafety.SAFE:
            continue
        for edit in edits_for_finding(finding):
            relative_file = relative_file_of(edit.file, ctx.root)
            if relative_file is None or not editable_file(
                ctx, relative_file, finding.category
            ):
                continue
            absolute_file = absolute_file_of(relative_file, ctx.root)
            by_file[relative_file].append(prepare_edit(
                absolute_file.read_text(encoding="utf-8") if absolute_file.exists() else "",
                edit,
                relative_file
            ))
    return dict(by_file)


def apply_edits(by_file: dict[str, list[PreparedEdit]], root: Path) -> list[str]:
    """
    Apply prepared edits and return modified paths.

    :param by_file: Mapping of repository-relative file paths to prepared edits.
    :param root: Repository root path.
    :returns: Sorted list of modified repository-relative POSIX paths.
    :raises ValueError: If edits overlap.
    """
    modified: set[str] = set()
    for relative_file, edits in by_file.items():
        sorted_edits = sorted(
            edits,
            key=lambda edit: (edit.edit.start_line, edit.edit.start_column),
            reverse=True,
        )
        for index, edit in enumerate(sorted_edits):
            if index > 0 and sorted_edits[index - 1].start_offset < edit.end_offset:
                raise ValueError(f"overlapping edits for {relative_file}")
        absolute_file = absolute_file_of(relative_file, root)
        original = (
            absolute_file.read_text(encoding="utf-8")
            if absolute_file.exists()
            else None
        )
        text = original or ""
        for edit in sorted_edits:
            text = (
                f"{text[: edit.start_offset]}"
                f"{edit.edit.replacement}"
                f"{text[edit.end_offset :]}"
            )
        if original != text:
            absolute_file.parent.mkdir(parents=True, exist_ok=True)
            absolute_file.write_text(text, encoding="utf-8")
            modified.add(relative_file)
    return sorted(modified)


def main() -> None:
    """
    Read manifest, validate applicable rules, and apply safe formatter edits.

    :returns: None
    """
    root = Path.cwd()
    manifest_path = root / MANIFEST_PATH
    if not manifest_path.is_file() or manifest_path.is_symlink():
        raise SystemExit(f"failed to read {MANIFEST_PATH}")
    ctx = create_rule_context(root, json.loads(manifest_path.read_text(encoding="utf-8")), stack="python")
    try:
        modified = apply_edits(collect_safe_edits(ctx, collect_findings(ctx)), ctx.root)
    except ValueError as error:
        raise SystemExit(f"format infrastructure failure: {error}") from error
    if modified:
        print(f"formatted: {len(modified)}")
        for path in modified:
            print(f"  {path}")
    else:
        print("no files formatted")
    sys.exit(0)


if __name__ == "__main__":
    main()
