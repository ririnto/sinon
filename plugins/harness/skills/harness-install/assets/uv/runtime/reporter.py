# -*- coding: utf-8 -*-

from __future__ import annotations

from collections.abc import Iterable
from pathlib import Path
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from harness_check_rule import Finding, FindingEdit


def severity_label(severity: str) -> str:
    """
    Map severity value to uppercase label for output.

    :param severity: Severity level string (ERROR/WARN/INFO).
    :rtype: str
    """
    return severity.upper()


def format_location_header(finding: "Finding") -> str:
    """
    Format the first line of a finding with location information.

    :param finding: The finding to format.
    :rtype: str
    """
    label = severity_label(finding.severity)
    msg_part = f"{finding.category}: {finding.message}"
    if finding.file is None:
        return f"[{label}] {msg_part}"
    if finding.start_line is None:
        return f"{finding.file} [{label}] {msg_part}"
    col = finding.start_column if finding.start_column is not None else 1
    return f"{finding.file}:{finding.start_line}:{col} [{label}] {finding.category}: {finding.message}"


def format_code_snippet(root: Path, finding: "Finding") -> list[str]:
    """
    Format code snippet with context lines around offending line.

    :param root: Repository root path.
    :param finding: The finding with location info.
    :rtype: list[str]
    """
    if finding.file is None or finding.start_line is None:
        return []
    file_path = root / finding.file
    if not file_path.is_file():
        return []
    lines = file_path.read_text(encoding="utf-8").splitlines()
    line_idx = finding.start_line - 1
    if line_idx < 0 or line_idx >= len(lines):
        return []
    start_idx = max(0, line_idx - 1)
    end_idx = min(len(lines), line_idx + 2)
    snippet_lines = lines[start_idx:end_idx]
    max_line_no = end_idx
    line_width = len(str(max_line_no))
    result = []
    for offset, code_line in enumerate(snippet_lines):
        actual_line_no = start_idx + offset + 1
        is_offending = actual_line_no == finding.start_line
        prefix = ">" if is_offending else " "
        line_no_str = str(actual_line_no).rjust(line_width)
        result.append(f"  {prefix} {line_no_str}  │ {code_line}")
    return result


def extract_removed_text(root: Path, edit: "FindingEdit") -> list[str]:
    """
    Extract the original text at the edit location from the source file.

    :param root: Repository root path.
    :param edit: Edit operation with location coordinates.
    :returns: Original text spanning the edit range, one entry per source line.
    :rtype: list[str]
    """
    file_path = root / edit.file
    if not file_path.is_file():
        return []
    file_lines = file_path.read_text(encoding="utf-8").splitlines()
    start_idx = edit.start_line - 1
    end_idx = edit.end_line - 1
    if start_idx < 0 or end_idx >= len(file_lines):
        return []
    removed: list[str] = []
    for i in range(start_idx, end_idx + 1):
        line = file_lines[i]
        if i == start_idx and i == end_idx:
            slice_text = line[
                min(edit.start_column - 1, len(line)) : min(edit.end_column, len(line))
            ]
        elif i == start_idx:
            slice_text = line[min(edit.start_column - 1, len(line)) :]
        elif i == end_idx:
            slice_text = line[: min(edit.end_column, len(line))]
        else:
            slice_text = line
        removed.append(slice_text)
    return removed


def format_fix_info(root: Path, finding: "Finding") -> list[str]:
    """
    Format fix information (Safety, Help, Before/After).

    :param root: Repository root path.
    :param finding: The finding with optional fix metadata.
    :rtype: list[str]
    """
    if finding.fix is None:
        return []
    result = [
        f"  Safety: {finding.fix.safety.value}",
        f"  Help: {finding.fix.description}",
    ]
    if finding.fix.edits:
        first_edit = finding.fix.edits[0]
        result.append("")
        result.append("  Before:")
        for line in extract_removed_text(root, first_edit):
            result.append(f"  - {line}")
        result.append("  After:")
        added_lines = (
            first_edit.replacement.split("\n") if first_edit.replacement else [""]
        )
        for line in added_lines:
            result.append(f"  + {line}")
    return result


def render_finding(root: Path, finding: "Finding") -> list[str]:
    """
    Render a single finding in structured diagnostic format.

    :param root: Repository root path.
    :param finding: The finding to render.
    :rtype: list[str]
    """
    result = [format_location_header(finding)]
    snippet = format_code_snippet(root, finding)
    if snippet:
        result.append("")
        result.extend(snippet)
    fix_info = format_fix_info(root, finding)
    if fix_info:
        result.append("")
        result.extend(fix_info)
    return result


def render_findings(root: Path, findings: Iterable["Finding"]) -> list[str]:
    """
    Render all findings in structured diagnostic format.

    Returns list of output lines suitable for printing. Gracefully handles
    findings where location or fix data is absent.

    :param root: Repository root path.
    :param findings: Iterable of findings to render.
    :rtype: list[str]
    """
    findings_list = list(findings)
    if not findings_list:
        return ["OK"]
    output = []
    for i, finding in enumerate(findings_list):
        if i > 0:
            output.append("")
        output.extend(render_finding(root, finding))
    file_count = len(set(f.file for f in findings_list if f.file is not None))
    error_count = sum(1 for f in findings_list if f.severity == "ERROR")
    warn_count = sum(1 for f in findings_list if f.severity == "WARN")
    info_count = sum(1 for f in findings_list if f.severity == "INFO")
    import harness_check_rule

    fixable_count = sum(
        1
        for f in findings_list
        if f.fix is not None and f.fix.safety == harness_check_rule.FixSafety.SAFE
    )
    summary = f"Checked {file_count} file(s). {len(findings_list)} violation(s): {error_count} error, {warn_count} warn, {info_count} info."
    if fixable_count > 0:
        summary += f" [*] {fixable_count} fixable."
    output.append("")
    output.append(summary)
    return output
