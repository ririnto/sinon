"""Consolidated spec-driven-development validation toolkit."""

import dataclasses
import datetime
import os
import re
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import yaml

LINE_SPLIT_RE = re.compile(r"\r?\n")
LEADING_BOM_RE = re.compile(r"^\uFEFF")
FRONTMATTER_DELIMITER_RE = re.compile(r"^---[ \t]*$")
FENCED_CODE_BLOCK_RE = re.compile(r"^[ \t]*(`{3,}|~{3,})")

MAX_YAML_ALIAS_COUNT = 100

# Linking constants
REVERSE_LINKS_HEADING_RE = re.compile(
    r"^#{2,6}\s+(Called By|Incoming Links|Inbound Links|Inbound References|Backlinks)\s*$",
    re.IGNORECASE,
)
REVERSE_LINKS_BULLET_RE = re.compile(
    r"^[ \t]*[-*]\s*(Called By|Incoming Links|Inbound Links|Inbound References|Backlinks)\s*(?::\s*|$)",
    re.IGNORECASE,
)
DEPRECATED_LINK_MAINTENANCE_HEADING_RE = re.compile(
    r"^#{2,6}\s+(?:Deprecated\s+)?Link(?:-| )Maintenance(?:\s+\(Deprecated\))?\s*$",
    re.IGNORECASE,
)
RELATIVE_LINK_SCHEME_RE = re.compile(r"^[a-zA-Z][a-zA-Z0-9+.-]*:")
WINDOWS_ABSOLUTE_PATH_RE = re.compile(r"^[A-Za-z]:[\\/]")
MAX_BULLET_HITS = 3

# Changelog constants
CHANGELOG_ENTRY_HEADING_RE = re.compile(
    r"^[ \t]{0,3}##[ \t]+\d{4}-\d{2}-\d{2}[ \t]+-[ \t]+\S.*$",
    re.MULTILINE,
)
CHANGELOG_ENTRY_HEADING_CAPTURE_RE = re.compile(
    r"^[ \t]{0,3}##[ \t]+(?P<date>\d{4}-\d{2}-\d{2})[ \t]+-[ \t]+\S.*$",
    re.MULTILINE,
)

# Contract validation constants
CONTRACT_UNITS_SECTION_RE = re.compile(
    r"^[ \t]{0,3}##[ \t]+Contract[ \t]+Units[ \t]*$",
    re.MULTILINE,
)
CONTRACT_UNIT_SECTION_RE = re.compile(
    r"^[ \t]{0,3}###[ \t]+Unit:[ \t]+(?P<unit_name>\S.*)$",
    re.MULTILINE,
)
CONTRACT_EXAMPLES_SECTION_RE = re.compile(
    r"^[ \t]{0,3}##[ \t]+Examples[ \t]+by[ \t]+Unit[ \t]+and[ \t]+Scenario[ \t]*$",
    re.MULTILINE,
)
CONTRACT_EXAMPLE_CASE_RE = re.compile(
    r"^[ \t]{0,3}###[ \t]+(?!unit[ \t]*:)[^:\n]+:"
    r"[ \t]+[^/\n]+[ \t]*\/[ \t]*\S[^\n]*$",
    re.IGNORECASE | re.MULTILINE,
)

CONTRACT_UNIT_REQUIRED_SUBSECTIONS: list[tuple[str, re.Pattern[str]]] = [
    (
        "Input Rules",
        re.compile(
            r"^[ \t]{0,3}####[ \t]+Input[ \t]+Rules"
            r"(?:[ \t]*(?::|[-/]|[([]).*)?[ \t]*$",
            re.IGNORECASE | re.MULTILINE,
        ),
    ),
    (
        "Output Rules",
        re.compile(
            r"^[ \t]{0,3}####[ \t]+Output[ \t]+Rules"
            r"(?:[ \t]*(?::|[-/]|[([]).*)?[ \t]*$",
            re.IGNORECASE | re.MULTILINE,
        ),
    ),
    (
        "Failure Modes",
        re.compile(
            r"^[ \t]{0,3}####[ \t]+Failure[ \t]+Modes"
            r"(?:[ \t]*(?::|[-/]|[([]).*)?[ \t]*$",
            re.IGNORECASE | re.MULTILINE,
        ),
    ),
    (
        "Behavioral Constraints",
        re.compile(
            r"^[ \t]{0,3}####[ \t]+Behavioral[ \t]+Constraints"
            r"(?:[ \t]*(?::|[-/]|[([]).*)?[ \t]*$",
            re.IGNORECASE | re.MULTILINE,
        ),
    ),
    (
        "Scenario Mapping",
        re.compile(
            r"^[ \t]{0,3}####[ \t]+Scenario[ \t]+Mapping"
            r"(?:[ \t]*(?::|[-/]|[([]).*)?[ \t]*$",
            re.IGNORECASE | re.MULTILINE,
        ),
    ),
]

# Validation core constants
TODO_COMMENT_PATTERNS: list[re.Pattern[str]] = [
    re.compile(r"<!--[ \t]*todo(?:\b|:)", re.IGNORECASE),
    re.compile(r"/\*[ \t]*todo(?:\b|:)", re.IGNORECASE),
    re.compile(r"^[ \t]*//[ \t]*todo(?:\b|:)", re.IGNORECASE),
    re.compile(r"^[ \t]*#[ \t]*todo(?:\b|:)", re.IGNORECASE),
    re.compile(r"^[ \t]*--[ \t]*todo(?:\b|:)", re.IGNORECASE),
]
TEXT_TODO_MARKER_RE = re.compile(
    r"(?<![A-Za-z0-9_./#-])todo:(?!\/\/)",
    re.IGNORECASE,
)
PLACEHOLDER_RE = re.compile(r"\{\{[^}]+\}\}")
ISO_8601_DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
MARKDOWNLINT_INLINE_DIRECTIVE_RE = re.compile(
    r"<!--[ \t]*markdownlint-[a-z0-9_-]+\b",
    re.IGNORECASE,
)
MANUAL_NUMBERED_HEADING_RE = re.compile(
    r"^[ \t]{0,3}#{2,6}\s+\d+\.\s+\S.*$",
)
TOKEN_WRAPPER_TRIM_CHARS = "[](){}<>\"'.,;:"
WHITESPACE_RE = re.compile(r"\s")
URL_WITH_SCHEME_RE = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*://")

# Scaffolding constants
SPEC_SCAFFOLDING_LINES: frozenset[str] = frozenset(
    [
        "Describe why this SPEC is needed and what problem it solves.",
        "Describe the role this SPEC plays in the system.",
        "Describe the capability and boundary this SPEC covers.",
        "Summarize scope and key concepts.",
        "Define functional requirements.",
        "Ensure every requirement is verifiable.",
        "Add at least one concrete verification example for this requirement.",
        "List the scenarios that verify this requirement.",
        "Describe major scenarios.",
        "State which requirement or requirements each flow satisfies.",
        "Describe the primary success path step by step.",
        "Describe valid alternate paths and branching behavior.",
        "Describe failure paths, error triggers, and expected outcomes.",
        "Define core data models or entities.",
        "For each entity, include purpose, key fields, and invariants.",
        "Document externally meaningful constraints and guarantees.",
        "Add domain, compatibility, security, performance, interoperability, or operational constraints when they materially affect behavior.",
        "Avoid unnecessary language, framework, library, or code-style constraints here unless they are explicitly requested or materially required.",
    ]
)

# Research validation constants
ALLOWED_RESEARCH_CATEGORIES = frozenset(["framework", "library", "topic"])
SENTENCE_SEGMENT_SPLIT_RE = re.compile(r"[;,.!?]")
URI_SCHEME_RE = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*$")
RESEARCH_TRAILING_COLON_RE = re.compile(r":$")
RESEARCH_SUBJECT_NAME_COMPOUND_EXEMPTION_RE = re.compile(
    r"\b(?:repository|project|codebase|workspace|local)\s+"
    r"(?:pattern|design|architecture|protocol|abstraction|layer|model)\b",
    re.IGNORECASE,
)
SUBJECT_TOKEN_SPLIT_RE = re.compile(r"[^a-z0-9]+")

RESEARCH_FORBIDDEN_HEADING_PATTERNS = [
    re.compile(
        r"^[ \t]{0,3}#{2,6}[ \t]+"
        r"(?:Project(?:[ \t-]+to[ \t-]+Project)?[ \t]+Comparison"
        r"|Repository[ \t]+Audit)s?[ \t]*$",
        re.IGNORECASE,
    ),
    re.compile(
        r"^[ \t]{0,3}#{2,6}[ \t]+"
        r"(?:(?:Implementation|Rollout|Delivery)[ \t]+Plan(?:ning)?"
        r"|Migration(?:[ \t]+Plan(?:ning)?|[ \t]+Sequencing))[ \t]*$",
        re.IGNORECASE,
    ),
    re.compile(
        r"^[ \t]{0,3}#{2,6}[ \t]+Task(?:[ \t]+Management|[ \t]+Breakdown)?[ \t]*$",
        re.IGNORECASE,
    ),
]

RESEARCH_FORBIDDEN_LABEL_PATTERNS = [
    re.compile(
        r"^[ \t]*(?!#)(?:[-*][ \t]+)?"
        r"(?:Project(?:[ \t-]+to[ \t-]+Project)?[ \t]+Comparison"
        r"|Repository[ \t]+Audit)s?(?:[ \t]*:|[ \t]+[-/])[ \t]+\S.*$",
        re.IGNORECASE,
    ),
    re.compile(
        r"^[ \t]*(?!#)(?:[-*][ \t]+)?"
        r"(?:(?:Implementation|Rollout|Delivery)[ \t]+Plan(?:ning)?"
        r"|Migration(?:[ \t]+Plan(?:ning)?|[ \t]+Sequencing))"
        r"(?:[ \t]*:|[ \t]+[-/])[ \t]+\S.*$",
        re.IGNORECASE,
    ),
    re.compile(
        r"^[ \t]*(?!#)(?:[-*][ \t]+)?Task(?:[ \t]+Management|[ \t]+Breakdown)?"
        r"(?:[ \t]*:|[ \t]+[-/])[ \t]+\S.*$",
        re.IGNORECASE,
    ),
]

RESEARCH_BOUNDARY_NEGATION_RE = re.compile(
    r"\b(?:must not|should not|do not|does not|did not|not a|"
    r"non-goals?|out of scope|avoid|avoids|without)\b",
    re.IGNORECASE,
)

RESEARCH_FORBIDDEN_PROSE_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    (
        "project/repository comparison",
        re.compile(
            r"\b(?:project|repository|repo)\b[^\n]{0,80}"
            r"\b(?:comparison|compare|compared|comparing|versus|vs\.?)\b"
            r"|\b(?:comparison|compare|compared|comparing|versus|vs\.?)\b"
            r"[^\n]{0,80}\b(?:project|repository|repo)\b",
            re.IGNORECASE,
        ),
    ),
    (
        "implementation or migration planning",
        re.compile(
            r"\b(?:implementation|rollout|delivery)\b[^\n]{0,80}"
            r"\b(?:plan(?:ning)?|phase(?:s)?|sequence|sequencing|timeline|milestone|"
            r"task(?:s)?|ship|deliver|roll(?:[ \t-]+out)?|migrate)\b"
            r"|\b(?:ship|deliver|implement|roll(?:[ \t-]+out)?|migrate)\b"
            r"[^\n]{0,80}\b(?:(?:one|two|three|four|[1-4])[ \t-]+phase(?:s)?"
            r"|phase[ \t]+[1-4]|timeline|milestone|task(?:s)?)\b",
            re.IGNORECASE,
        ),
    ),
    (
        "task-management planning",
        re.compile(
            r"\b(?:task(?:s)?|todo|checklist|work item(?:s)?|owner(?:s)?)\b"
            r"[^\n]{0,40}"
            r"\b(?:assign|assigned|assignment|track|tracking|manage|managed|"
            r"next step(?:s)?|due date(?:s)?)\b"
            r"|\b(?:assign|track|manage)\b[^\n]{0,40}"
            r"\b(?:task(?:s)?|todo|checklist|work item(?:s)?)\b",
            re.IGNORECASE,
        ),
    ),
    (
        "project/repository audit",
        re.compile(
            r"\b(?:project|repository|repo)\b[^\n]{0,80}\baudit(?:s|ed|ing)?\b"
            r"|\baudit(?:s|ed|ing)?\b[^\n]{0,80}"
            r"\b(?:project|repository|repo)\b",
            re.IGNORECASE,
        ),
    ),
]

RESEARCH_FRONTMATTER_FORBIDDEN_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    (
        "project/repository comparison",
        re.compile(
            r"\b(?:project|repository|repo)\b[^\n]{0,80}"
            r"\b(?:comparison|compare|compared|comparing|versus|vs\.?)\b"
            r"|\b(?:comparison|compare|compared|comparing|versus|vs\.?)\b"
            r"[^\n]{0,80}\b(?:project|repository|repo)\b",
            re.IGNORECASE,
        ),
    ),
    (
        "project/repository audit",
        re.compile(
            r"\b(?:project|repository|repo)\b[^\n]{0,80}\baudit(?:ing|ed|s)?\b"
            r"|\baudit(?:ing|ed|s)?\b[^\n]{0,80}"
            r"\b(?:project|repository|repo)\b",
            re.IGNORECASE,
        ),
    ),
    (
        "implementation planning",
        re.compile(
            r"\b(?:implementation|rollout|delivery)\b[^\n]{0,60}\bplan(?:ning)?\b"
            r"|\b(?:plan(?:ning)?|roadmap|timeline|milestone)\b[^\n]{0,60}"
            r"\b(?:implementation|rollout|delivery)\b",
            re.IGNORECASE,
        ),
    ),
    (
        "migration sequencing",
        re.compile(
            r"\bmigration\b[^\n]{0,40}"
            r"\b(?:plan(?:ning)?|sequence|sequencing|phase(?:s)?)\b"
            r"|\b(?:sequence|sequencing|phase(?:s)?)\b[^\n]{0,40}\bmigration\b",
            re.IGNORECASE,
        ),
    ),
]

RESEARCH_EXTERNAL_SUBJECT_NEGATIVE_PATTERN = re.compile(
    r"\b(?:project|repository|repo|codebase|workspace|local)\b",
    re.IGNORECASE,
)

# OpenAPI-specific patterns
OPENAPI_SCHEMA_REF_RE = re.compile(
    r"^#/components\/schemas\/(?P<name>[^/\s]+)$"
)
OPENAPI_REQUEST_BODY_REF_RE = re.compile(
    r"^#/components\/requestBodies\/(?P<name>[^/\s]+)$"
)
OPENAPI_RESPONSE_REF_RE = re.compile(
    r"^#/components\/responses\/(?P<name>[^/\s]+)$"
)
OPENAPI_ALL_OF_COMPOSITION_KEY = "allOf"
OPENAPI_BRANCH_COMPOSITION_KEYS = ("oneOf", "anyOf")
OPENAPI_STATUS_DEFAULT = "default"
OPENAPI_STATUS_CODE_RE = re.compile(r"^[1-5]\d{2}$")
OPENAPI_STATUS_RANGE_RE = re.compile(r"^[1-5]XX$")
OPENAPI_NO_BODY_STATUS_CODES = frozenset([204, 205, 304])
OPENAPI_HTTP_METHODS = frozenset([
    "get", "put", "post", "delete", "options", "head", "patch", "trace",
])
OPENAPI_FIELD_SCHEMA_COMPOSITION_KEYS = ("allOf", "oneOf", "anyOf", "not")

# OpenAPI TODO detection patterns (same as validation module)
OPENAPI_TODO_COMMENT_PATTERNS = [
    re.compile(r"<!--[ \t]*todo(?:\b|:)", re.IGNORECASE),
    re.compile(r"/\*[ \t]*todo(?:\b|:)", re.IGNORECASE),
    re.compile(r"^[ \t]*//[ \t]*todo(?:\b|:)", re.IGNORECASE),
    re.compile(r"^[ \t]*#[ \t]*todo(?:\b|:)", re.IGNORECASE),
    re.compile(r"^[ \t]*--[ \t]*todo(?:\b|:)", re.IGNORECASE),
]
OPENAPI_TEXT_TODO_MARKER_RE = re.compile(r"(?<![A-Za-z0-9_./#-])todo:(?!\/\/)", re.IGNORECASE)
OPENAPI_PLACEHOLDER_RE = re.compile(r"\{\{[^}]+\}\}")
OPENAPI_WHITESPACE_RE = re.compile(r"\s")
OPENAPI_URL_WITH_SCHEME_RE = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*://")
OPENAPI_TOKEN_WRAPPER_TRIM_CHARS = "[](){}<>\"'.,;"
OPENAPI_RELATIVE_LINK_SCHEME_RE = re.compile(r"^[a-zA-Z][a-zA-Z0-9+.-]:")


@dataclasses.dataclass
class FrontmatterBlock:
    """Result of extracting a YAML frontmatter block from Markdown text.

    :param end_line: One-based line number where the closing delimiter ends.
    :param yaml: Raw YAML content between the delimiters.
    """
    end_line: int
    yaml: str


@dataclasses.dataclass
class FileValidationResult:
    """Result of validating a single file.

    :param errors: List of validation error strings.
    :param passed: True when no errors were found.
    """
    errors: list[str]
    passed: bool


def is_frontmatter_delimiter(line: str) -> bool:
    # :description: Checks if a line is a YAML frontmatter delimiter.
    # :param line: A single line of text.
    # :return: True if the line is a frontmatter delimiter.
    return bool(FRONTMATTER_DELIMITER_RE.match(line))


def extract_markdown_body(text: str) -> str:
    """Extracts the Markdown body from text, removing YAML frontmatter.

    :param text: Raw Markdown text possibly containing frontmatter.
    :return: Markdown body with frontmatter removed.
    """
    lines = LINE_SPLIT_RE.split(text)
    if not lines:
        return text
    first = LEADING_BOM_RE.sub("", lines[0]).strip()
    if first != "---":
        return text
    end_index = -1
    for idx in range(1, len(lines)):
        if is_frontmatter_delimiter(lines[idx]):
            end_index = idx
            break
    if end_index == -1:
        return text
    return "\n".join(lines[end_index + 1:])


def escape_reg_exp(value: str) -> str:
    """Escapes special characters in a string for use in a regular expression.

    :param value: The string to escape.
    :return: The escaped string safe for use in regex patterns.
    """
    return re.escape(value)


def remove_fenced_code_blocks(text: str) -> str:
    """Removes fenced code blocks from Markdown text.

    :param text: Raw Markdown text.
    :return: Text with fenced code block contents removed.
    """
    lines_out: list[str] = []
    fence_char = ""
    fence_len = 0
    for line in LINE_SPLIT_RE.split(text):
        if fence_char:
            close_pattern = re.compile(
                r"^[ \t*" + re.escape(fence_char) + r"{" + str(fence_len) + r",}[ \t]*$"
            )
            if close_pattern.match(line):
                fence_char = ""
                fence_len = 0
            continue
        fence_match = FENCED_CODE_BLOCK_RE.match(line)
        if fence_match:
            marker = fence_match.group(1)
            fence_char = marker[0]
            fence_len = len(marker)
            continue
        lines_out.append(line)
    return "\n".join(lines_out)


def extract_frontmatter_from_text(text: str) -> FrontmatterBlock | None:
    """Extracts the first YAML frontmatter block from Markdown text.

    :param text: Raw Markdown text.
    :return: FrontmatterBlock if found, None otherwise.
    """
    if not text:
        return None
    lines = LINE_SPLIT_RE.split(text)
    if not lines:
        return None
    first = re.sub(r"^\uFEFF", "", lines[0]).strip()
    if first != "---":
        return None
    end_index = -1
    for index in range(1, len(lines)):
        if re.match(r"^---[ \t]*$", lines[index]):
            end_index = index
            break
    if end_index == -1:
        raise ValueError("Unterminated YAML frontmatter")
    return FrontmatterBlock(
        yaml="\n".join(lines[1:end_index]),
        end_line=end_index + 1,
    )


def extract_frontmatter_from_file(file_path: str) -> FrontmatterBlock | None:
    """Reads a Markdown file and extracts its first YAML frontmatter block.

    :param file_path: Path to the Markdown file.
    :return: FrontmatterBlock if found, None otherwise.
    """
    with open(file_path, "r", encoding="utf-8") as f:
        return extract_frontmatter_from_text(f.read())


def parse_yaml_document(text: str) -> tuple[Any | None, str | None]:
    """Parses YAML text with the shared skill parser configuration.

    :param text: Raw YAML text.
    :return: Tuple of (parsed_data, error_message). error_message is None on success.
    """
    try:
        data = yaml.safe_load(text)
        return (data, None)
    except yaml.YAMLError:
        return (None, "Invalid YAML")


def find_spec_root(input_path: str) -> str | None:
    """Finds the spec root directory by walking up from the given path.

    :param input_path: Starting directory or file path.
    :return: Path to the nearest directory named ``spec``, or None if not found.
    """
    cursor = str(Path(input_path).resolve())
    while True:
        if os.path.basename(cursor) == "spec":
            return cursor
        parent = os.path.dirname(cursor)
        if parent == cursor:
            return None
        cursor = parent


def resolve_validation_roots(spec_path: str) -> tuple[str, str] | None:
    """Resolves the validation roots for a given spec path.

    :param spec_path: The spec file or directory path.
    :return: Tuple of (specRoot, scanRoot) if found, None otherwise.
    """
    resolved_path = str(Path(spec_path).resolve())
    in_spec_root = find_spec_root(resolved_path)
    if in_spec_root is not None:
        return (in_spec_root, resolved_path)
    local_spec_root = str(Path(resolved_path) / "spec")
    if os.path.isdir(local_spec_root):
        return (local_spec_root, local_spec_root)
    return None


def collect_files_by_name(root: str, name: str) -> list[str]:
    """Recursively collects all files with a given name under a root directory.

    :param root: Root directory to search under.
    :param name: Exact filename to match.
    :return: Sorted list of absolute paths to matching files.
    """
    out: list[str] = []
    def walk(dir_path: str) -> None:
        entries = sorted(os.scandir(dir_path), key=lambda e: e.name)
        for entry in entries:
            full_path = entry.path
            if entry.is_dir():
                walk(full_path)
            elif entry.is_file() and entry.name == name:
                out.append(str(Path(full_path).resolve()))
    walk(root)
    return out


def list_by_basename(root: str, basename: str) -> list[str]:
    """Lists all files with a given basename under a root directory, sorted.

    :param root: Root directory to search under.
    :param basename: Basename (filename) to match.
    :return: Sorted list of absolute paths to matching files.
    """
    return sorted(collect_files_by_name(root, basename))


def find_unresolved_spec_scaffolding(text: str) -> list[str]:
    """Returns unresolved scaffold fingerprint lines in authored SPEC content.

    :param text: Raw SPEC Markdown text.
    :return: List of up to 3 unique unresolved scaffold instruction lines.
    """
    non_fenced_body = remove_fenced_code_blocks(extract_markdown_body(text))
    hits: list[str] = []
    for line in LINE_SPLIT_RE.split(non_fenced_body):
        normalized = line.strip()
        if normalized in SPEC_SCAFFOLDING_LINES and normalized not in hits:
            hits.append(normalized)
        if len(hits) >= 3:
            break
    return hits


def validate_frontmatter_links(
    file_path: str,
    frontmatter: dict,
    spec_root: str | None,
) -> list[str]:
    """Validate the frontmatter call-link graph constraints for SPEC files.

    Ensures the ``call`` field exists as an array and each entry points to an
    existing ``SPEC.md`` within the spec root via a relative path.

    :param file_path: Absolute path of the file being validated.
    :param frontmatter: Parsed YAML frontmatter as a dict.
    :param spec_root: Spec root directory, or ``None`` to auto-detect.
    :return: List of error strings (empty when valid).
    """
    def has_own_linking(value: object, key: str) -> bool:
        return isinstance(value, dict) and key in value
    def extract_frontmatter_call_link(raw: object) -> str:
        if isinstance(raw, str):
            return raw.strip()
        if isinstance(raw, dict):
            path_value = raw.get("path")
            if isinstance(path_value, str):
                return path_value.strip()
        return ""
    def validate_frontmatter_link_target(link: str, link_target: str, effective_spec_root: str | None) -> str | None:
        if RELATIVE_LINK_SCHEME_RE.match(link_target):
            return (
                f"FAIL [{file_path}]: Frontmatter link must be a relative path"
                f" to SPEC.md: {link}"
            )
        if os.path.basename(link_target) != "SPEC.md":
            return (
                f"FAIL [{file_path}]: Frontmatter call entries are SPEC-to-SPEC only"
                f" (must point to SPEC.md): {link}"
            )
        if os.path.isabs(link_target) or WINDOWS_ABSOLUTE_PATH_RE.match(link_target):
            return (
                f"FAIL [{file_path}]: Frontmatter link must be a relative path:"
                f" {link}"
            )
        resolved_target = str(
            Path(os.path.dirname(file_path)).joinpath(link_target).resolve()
        )
        if effective_spec_root is not None:
            rel = os.path.relpath(resolved_target, effective_spec_root)
            if rel == ".." or rel.startswith(".."):
                return (
                    f"FAIL [{file_path}]: Frontmatter link target escapes spec root:"
                    f" {link}"
                )
        if not os.path.exists(resolved_target):
            return (
                f"FAIL [{file_path}]: Frontmatter link target not found: {link}"
            )
        if not os.path.isfile(resolved_target):
            return (
                f"FAIL [{file_path}]: Frontmatter link target is not a file: {link}"
            )
        if os.path.basename(resolved_target) != "SPEC.md":
            return (
                f"FAIL [{file_path}]: Frontmatter link target must be SPEC.md:"
                f" {link}"
            )
        return None
    if not has_own_linking(frontmatter, "call"):
        return [
            "FAIL [{}]: Frontmatter must include 'call'"
            " (use [] when there are no outbound references)".format(file_path)
        ]
    raw_calls = frontmatter.get("call") or []
    if not isinstance(raw_calls, list):
        return [
            "FAIL [{}]: Frontmatter 'call' must be an array"
            " (use [] when there are no outbound references)".format(file_path)
        ]
    errors: list[str] = []
    effective_spec_root = spec_root or find_spec_root(file_path)
    for raw in raw_calls:
        link = extract_frontmatter_call_link(raw)
        if not link:
            continue
        link_target = link.split("#", 1)[0].strip()
        if not link_target:
            continue
        link_error = validate_frontmatter_link_target(
            link, link_target, effective_spec_root
        )
        if link_error is not None:
            errors.append(link_error)
    return errors


def validate_no_reverse_direction_points(
    file_path: str,
    text: str,
) -> list[str]:
    """Validate that reverse-direction link sections and bullets are absent.

    Scans text outside of fenced code blocks for headings such as
    ``## Called By`` or bullet items like ``- Called By: ...``, reporting up to
    three bullet violations before stopping.

    :param file_path: Absolute path of the file being validated.
    :param text: Full Markdown text of the file.
    :return: List of error strings (empty when valid).
    """
    errors: list[str] = []
    non_fenced_text = remove_fenced_code_blocks(text)
    heading_match = REVERSE_LINKS_HEADING_RE.search(non_fenced_text)
    if heading_match:
        errors.append(
            "FAIL [{}]: Reverse-direction link sections are prohibited:"
            " {}".format(file_path, heading_match.group(0).strip())
        )
    bullet_hits = 0
    for match in REVERSE_LINKS_BULLET_RE.finditer(non_fenced_text):
        errors.append(
            "FAIL [{}]: Reverse-direction points are prohibited:"
            " {}".format(file_path, match.group(0).strip())
        )
        bullet_hits += 1
        if bullet_hits >= MAX_BULLET_HITS:
            break
    return errors


def validate_deprecated_link_sections(
    file_path: str,
    text: str,
) -> list[str]:
    """Validate that deprecated link-maintenance sections are absent.

    Scans text outside of fenced code blocks for headings matching
    ``## Link Maintenance`` or its deprecated variants.

    :param file_path: Absolute path of the file being validated.
    :param text: Full Markdown text of the file.
    :return: List of error strings (empty when valid).
    """
    errors: list[str] = []
    non_fenced_text = remove_fenced_code_blocks(text)
    heading_match = DEPRECATED_LINK_MAINTENANCE_HEADING_RE.search(non_fenced_text)
    if heading_match:
        errors.append(
            "FAIL [{}]: Deprecated link-maintenance section is prohibited:"
            " {}".format(file_path, heading_match.group(0).strip())
        )
    return errors


def to_posix_path_for_validation(input_path: str) -> str:
    """Convert a filesystem path to POSIX-style forward-slash separators.

    :param input_path: Path string using platform-specific separators.
    :return: The same path with all separators normalized to ``/``.
    """
    return input_path.replace(os.sep, "/")


def has_changelog_entry_heading(text: str) -> bool:
    """Check whether a changelog text contains at least one dated entry heading.

    Strips frontmatter and fenced code blocks before testing for the
    ``## YYYY-MM-DD - ...`` pattern.

    :param text: Raw Markdown text of the changelog file.
    :return: True when at least one dated entry heading is found.
    """
    body = extract_markdown_body(remove_fenced_code_blocks(text))
    return bool(CHANGELOG_ENTRY_HEADING_RE.search(body))


def validate_changelog_entry_order(
    file_path: str,
    text: str,
) -> list[str]:
    """Validate that changelog entries are ordered with the latest dates first.

    Extracts all ``## YYYY-MM-DD - ...`` headings from the body and confirms
    each successive date is not later than its predecessor.

    :param file_path: Absolute path of the changelog file.
    :param text: Raw Markdown text of the changelog file.
    :return: List of error strings (empty when dates are correctly ordered).
    """
    errors: list[str] = []
    body = extract_markdown_body(remove_fenced_code_blocks(text))
    dates: list[datetime.date] = []
    for match in CHANGELOG_ENTRY_HEADING_CAPTURE_RE.finditer(body):
        date_text = match.group("date")
        if date_text:
            try:
                parsed = datetime.date.fromisoformat(date_text)
                dates.append(parsed)
            except ValueError:
                pass
    for index in range(1, len(dates)):
        if dates[index] > dates[index - 1]:
            errors.append(
                "FAIL [{}]: CHANGELOG entries must be ordered with"
                " latest dates first".format(file_path)
            )
            break
    return errors


def validate_changelog_layout(
    spec_root: str,
) -> tuple[str | None, list[str]]:
    """Validate changelog layout constraints under a spec root directory.

    Checks that exactly one ``CHANGELOG.md`` exists at the spec root and that
    no other ``CHANGELOG.md`` files appear elsewhere under the spec tree.

    :param spec_root: Absolute path to the spec root directory.
    :return: Tuple of (root_changelog_path_or_None, error_list).
    """
    errors: list[str] = []
    root_changelog = os.path.join(spec_root, "CHANGELOG.md")
    root_file: str | None
    if not os.path.exists(root_changelog):
        errors.append(
            "FAIL: Missing required root changelog file:"
            f" {root_changelog}"
        )
        root_file = None
    elif os.path.isfile(root_changelog):
        root_file = root_changelog
    else:
        errors.append(f"FAIL: Root changelog path is not a file: {root_changelog}")
        root_file = None
    for candidate in sorted(collect_files_by_name(spec_root, "CHANGELOG.md")):
        if candidate == root_changelog:
            continue
        errors.append(
            f"FAIL [{candidate}]: CHANGELOG.md is only allowed at spec root"
            f" ({root_changelog})"
        )
    return (root_file, errors)


def label_contract_unit(match: re.Match[str], index: int) -> str:
    # :description: Produce a human-readable label for a contract unit match.
    # :param match: Regex match object for the ``### Unit: ...`` heading.
    # :param index: Zero-based index of this unit among all unit matches.
    # :return: The trimmed unit name, or a fallback label with the index.
    unit_name = (match.group("unit_name") or "").strip()
    return unit_name or f"(index {index + 1})"


def collect_contract_unit_order_errors(
    contract_file: str,
    unit_matches: list[re.Match[str]],
    examples_start: int,
) -> tuple[list[re.Match[str]], list[str]]:
    # :description: Check that no ``### Unit:`` sections appear after the Examples heading.
    # :param contract_file: Absolute path of the CONTRACT file.
    # :param unit_matches: All ``### Unit:`` regex matches in document order.
    # :param examples_start: Character offset of the Examples section heading.
    # :return: Tuple of (filtered_unit_matches, error_list).
    errors: list[str] = []
    late_units = [m for m in unit_matches if m.start() > examples_start]
    for late_unit in late_units[:3]:
        unit_name = (late_unit.group("unit_name") or "").strip()
        unit_label = unit_name or "(unnamed unit)"
        errors.append(
            'FAIL [{}]: CONTRACT.md unit {} appears after'
            " `## Examples by Unit and Scenario`".format(
                contract_file, repr(unit_label)
            )
        )
    filtered_units = [
        m for m in unit_matches if m.start() <= examples_start
    ]
    if not filtered_units:
        errors.append(
            "FAIL [{}]: CONTRACT.md must define at least one `### Unit: ...`"
            " section before `## Examples by Unit and Scenario`".format(
                contract_file
            )
        )
    return (filtered_units, errors)


def collect_contract_unit_subsection_errors(
    contract_file: str,
    non_fenced_body: str,
    unit_matches: list[re.Match[str]],
    unit_block_limit: int,
) -> list[str]:
    # :description: Verify each contract unit contains all required ``####`` subsections.
    # :param contract_file: Absolute path of the CONTRACT file.
    # :param non_fenced_body: Document text with fenced code blocks removed.
    # :param unit_matches: Ordered ``### Unit:`` matches to validate.
    # :param unit_block_limit: Character position where unit blocks end.
    # :return: List of error strings for any missing subsections.
    errors: list[str] = []
    for index, unit_match in enumerate(unit_matches):
        next_unit_start = (
            unit_matches[index + 1].start()
            if index + 1 < len(unit_matches)
            else unit_block_limit
        )
        unit_block = non_fenced_body[
            unit_match.start()
            + len(unit_match.group(0)) : next_unit_start
        ]
        unit_label = label_contract_unit(unit_match, index)
        for section_name, section_pattern in CONTRACT_UNIT_REQUIRED_SUBSECTIONS:
            if not section_pattern.search(unit_block):
                errors.append(
                    'FAIL [{}]: CONTRACT.md unit {} must include'
                    " `#### {}` (unit-specific suffixes are allowed)".format(
                        contract_file, repr(unit_label), section_name
                    )
                )
    return errors


def validate_contract_examples_section(
    contract_file: str,
    non_fenced_body: str,
    examples_match: re.Match[str] | None,
) -> list[str]:
    # :description: Validate the presence of at least one example case under the Examples heading.
    # :param contract_file: Absolute path of the CONTRACT file.
    # :param non_fenced_body: Document text with fenced code blocks removed.
    # :param examples_match: Match for the ``## Examples by Unit and Scenario``
    #     heading, or ``None`` if absent.
    # :return: List of error strings (empty when valid).
    if not examples_match:
        return [
            "FAIL [{}]: CONTRACT.md must include a"
            " `## Examples by Unit and Scenario` section".format(contract_file)
        ]
    examples_block = non_fenced_body[
        examples_match.start()
        + len(examples_match.group(0)) :
    ]
    if CONTRACT_EXAMPLE_CASE_RE.search(examples_block):
        return []
    return [
        "FAIL [{}]: CONTRACT.md must include at least one example subsection"
        " (`### <Example ID>: <Unit> / <Scenario>`)".format(contract_file)
    ]


def validate_contract_units_structure(
    contract_file: str,
    text: str,
) -> list[str]:
    """Validate CONTRACT.md unit structure and examples section layout.

    Performs the following checks on the provided Markdown body:

    - Presence of a ``## Contract Units`` heading.
    - At least one ``### Unit: <name>`` section.
    - No unit sections appearing after ``## Examples by Unit and Scenario``.
    - Each unit contains all five required ``####`` subsections
      (Input Rules, Output Rules, Failure Modes, Behavioral Constraints,
      Scenario Mapping).
    - An ``## Examples by Unit and Scenario`` section exists with at least
      one ``### <Example ID>: <Unit> / <Scenario>`` case.

    Frontmatter should already be stripped from *text* before calling this
    function.

    :param contract_file: Absolute path of the CONTRACT file being validated.
    :param text: Markdown body of the file (frontmatter already removed).
    :return: List of validation error strings (empty when fully valid).
    """
    errors: list[str] = []
    non_fenced_body = remove_fenced_code_blocks(text)
    examples_match = CONTRACT_EXAMPLES_SECTION_RE.search(non_fenced_body)
    examples_start = examples_match.start() if examples_match else -1
    if not CONTRACT_UNITS_SECTION_RE.search(non_fenced_body):
        errors.append(
            "FAIL [{}]: CONTRACT.md must include a"
            " `## Contract Units` section".format(contract_file)
        )
    unit_matches = list(CONTRACT_UNIT_SECTION_RE.finditer(non_fenced_body))
    if not unit_matches:
        errors.append(
            "FAIL [{}]: CONTRACT.md must include at least one"
            " `### Unit: ...` section".format(contract_file)
        )
    else:
        if examples_match is not None:
            ordered_units, order_errors = collect_contract_unit_order_errors(
                contract_file, unit_matches, examples_start
            )
            errors.extend(order_errors)
            unit_matches_for_structure = ordered_units
        else:
            unit_matches_for_structure = unit_matches
        unit_block_limit = (
            examples_start
            if examples_match is not None
            else len(non_fenced_body)
        )
        errors.extend(
            collect_contract_unit_subsection_errors(
                contract_file,
                non_fenced_body,
                unit_matches_for_structure,
                unit_block_limit,
            )
        )
    errors.extend(
        validate_contract_examples_section(
            contract_file, non_fenced_body, examples_match
        )
    )
    return errors


def validate_research_location(file_path: str) -> tuple[bool, str | None]:
    """Validates that a RESEARCH.md file resides under the required directory structure.

    The file must be located at
    ``spec/research/{framework|library|topic}/{name}/RESEARCH.md``
    relative to the repository root. Path resolution uses absolute paths
    converted to POSIX-style separators.

    :param file_path: Absolute or relative path to the RESEARCH.md file.
    :return: Tuple of (is_valid, error_message). error_message is None when valid.
    """
    marker = "/spec/research/"
    resolved = str(Path(file_path).resolve()).replace(os.sep, "/")
    marker_index = resolved.find(marker)
    if marker_index == -1:
        return (
            False,
            f"FAIL [{file_path}]: RESEARCH.md must be under "
            "spec/research/{framework|library|topic}/{name}/RESEARCH.md",
        )
    tail = resolved[marker_index + len(marker):]
    parts = [p for p in tail.split("/") if p]
    if len(parts) != 3:
        return (
            False,
            f"FAIL [{file_path}]: RESEARCH.md must be under "
            "spec/research/{framework|library|topic}/{name}/RESEARCH.md",
        )
    category = parts[0]
    if category not in ALLOWED_RESEARCH_CATEGORIES:
        return (
            False,
            f"FAIL [{file_path}]: Invalid research category '{category}'. "
            "Allowed: framework, library, topic",
        )
    if parts[2] != "RESEARCH.md":
        return (
            False,
            f"FAIL [{file_path}]: Research document name must be RESEARCH.md",
        )
    return (True, None)


def validate_research_subject_url(
    file_path: str,
    frontmatter: dict[str, Any],
) -> str | None:
    """Validates the ``subject.url`` field in RESEARCH frontmatter when present.

    If the frontmatter contains a ``subject`` object with a ``url`` field,
    this function validates that the URL is a well-formed URI.
    Absence of ``subject`` or ``subject.url`` is not an error.

    :param file_path: Path to the RESEARCH file for error messages.
    :param frontmatter: Parsed frontmatter dictionary.
    :return: An error message string, or None if validation passes or the field is absent.
    """
    subject = frontmatter.get("subject")
    if not (subject is not None and isinstance(subject, dict) and not isinstance(subject, list)):
        return None
    if "url" not in subject:
        return None
    raw_value = subject["url"]
    if not isinstance(raw_value, str) or not is_valid_uri_research(raw_value):
        return (
            f"FAIL [{file_path}]: subject.url must be a valid URI "
            "(for example, https://example.com/reference)"
        )
    return None


def is_valid_uri_research(value: str) -> bool:
    # :description: Validates that a string is a well-formed URI with scheme and host or pathname.
    # :param value: The URI string to validate.
    # :return: True if the string represents a valid URI.
    text = value.strip()
    if not text or WHITESPACE_RE.search(text):
        return False
    try:
        url = urlparse(text)
    except Exception:
        return False
    if not url.scheme:
        return False
    scheme = RESEARCH_TRAILING_COLON_RE.sub("", url.scheme)
    if not URI_SCHEME_RE.match(scheme):
        return False
    if not (url.netloc or url.path):
        return False
    if scheme in ("http", "https") and not url.netloc:
        return False
    return True


def push_bounded_error(errors: list[str], message: str, max_count: int = 3) -> bool:
    # :description: Appends an error message to the error list and signals when the cap is reached.
    # :param errors: Mutable list accumulating error messages.
    # :param message: The error message to append.
    # :param max_count: Maximum number of errors before signaling stop.
    # :return: True if the error count has reached or exceeded the maximum.
    errors.append(message)
    return len(errors) >= max_count


def collect_pattern_boundary_errors(
    source_text: str,
    patterns: list[re.Pattern[str]],
    create_message: Any,
    errors: list[str],
) -> bool:
    # :description: Scans text against a list of patterns, collecting the first matching boundary violation.
    # :param source_text: The text to scan for forbidden patterns.
    # :param patterns: List of compiled regex patterns to test against.
    # :param createMessage: Callable that produces an error message from the matched text.
    # :param errors: Mutable list accumulating error messages.
    # :return: True if the error cap was reached and scanning should stop.
    for pattern in patterns:
        match = pattern.search(source_text)
        if not match:
            continue
        if push_bounded_error(errors, create_message(match.group(0).strip())):
            return True
    return False


def validate_research_prose_segment(
    file_path: str,
    segment: str,
    errors: list[str],
) -> bool:
    # :description: Validates a single prose segment against forbidden research patterns with negation awareness.
    # :param file_path: Path to the RESEARCH file for error messages.
    # :param segment: A single sentence-level text segment to validate.
    # :param errors: Mutable list accumulating error messages.
    # :return: True if the error cap was reached and outer loop should stop.
    for label, pattern in RESEARCH_FORBIDDEN_PROSE_PATTERNS:
        match = pattern.search(segment)
        if not match:
            continue
        prefix = segment[:match.start()]
        if RESEARCH_BOUNDARY_NEGATION_RE.search(prefix):
            continue
        return push_bounded_error(
            errors,
            f"FAIL [{file_path}]: RESEARCH.md must remain framework/library/topic "
            f"investigation, not {label} content such as {segment}",
        )
    return False


def validate_research_prose_boundaries(
    file_path: str,
    source_text: str,
    errors: list[str],
) -> None:
    # :description: Validates all non-heading, non-empty lines in RESEARCH body text against forbidden prose patterns.
    # :param file_path: Path to the RESEARCH file for error messages.
    # :param sourceText: Full body text (with frontmatter stripped and code blocks removed).
    # :param errors: Mutable list accumulating error messages.
    for raw_line in LINE_SPLIT_RE.split(source_text):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        segments = [
            seg.strip()
            for seg in SENTENCE_SEGMENT_SPLIT_RE.split(line)
            if seg.strip()
        ]
        for segment in segments:
            if validate_research_prose_segment(file_path, segment, errors):
                return


def get_forbidden_frontmatter_label(text: str) -> str | None:
    # :description: Checks frontmatter text against forbidden patterns and returns the first matched category label.
    # :param text: The frontmatter field text to validate.
    # :return: The label of the first matching forbidden pattern, or None if no match.
    for label, pattern in RESEARCH_FRONTMATTER_FORBIDDEN_PATTERNS:
        if pattern.search(text):
            return label
    return None


def validate_research_frontmatter_field(
    file_path: str,
    field_label: str,
    raw_value: Any,
    errors: list[str],
) -> bool:
    # :description: Validates a single frontmatter field value against forbidden boundary patterns.
    # :param file_path: Path to the RESEARCH file for error messages.
    # :param field_label: The frontmatter field name (e.g. ``title``, ``description``).
    # :param raw_value: The raw field value from parsed frontmatter.
    # :param errors: Mutable list accumulating error messages.
    # :return: True if an error was pushed and the caller should consider stopping.
    if not isinstance(raw_value, str):
        return False
    text = raw_value.strip()
    if not text:
        return False
    label = get_forbidden_frontmatter_label(text)
    if not label:
        return False
    return push_bounded_error(
        errors,
        f"FAIL [{file_path}]: RESEARCH.md frontmatter {field_label} must remain "
        f"framework/library/topic investigation, not {label} content such as {text}",
    )


def validate_research_subject_name_boundary(
    file_path: str,
    subject_name: str,
    errors: list[str],
) -> None:
    # :description: Validates that subject.name identifies an external subject rather than local repository context.
    # :param file_path: Path to the RESEARCH file for error messages.
    # :param subject_name: The subject.name value from frontmatter.
    # :param errors: Mutable list accumulating error messages.
    text = subject_name.strip()
    if not text:
        return
    if validate_research_frontmatter_field(file_path, "subject.name", text, errors):
        return
    if (
        RESEARCH_EXTERNAL_SUBJECT_NEGATIVE_PATTERN.search(text)
        and not RESEARCH_SUBJECT_NAME_COMPOUND_EXEMPTION_RE.search(text)
    ):
        push_bounded_error(
            errors,
            f"FAIL [{file_path}]: RESEARCH.md frontmatter subject.name must identify "
            f"an external framework/library/topic, not local repository context such as {text}",
        )


def validate_research_scope_boundaries(file_path: str, text: str) -> list[str]:
    """Validates RESEARCH body content against forbidden heading, label, and prose boundary patterns.

    This checks that the Markdown body (with fenced code blocks removed)
    does not contain headings or labels indicating project comparison,
    implementation/migration planning, task management, or repository audit
    scope. Prose segments are also scanned for these patterns with negation
    awareness so that sentences explicitly ruling out such scope are allowed.

    Validation stops after collecting up to 3 errors.

    :param file_path: Path to the RESEARCH file for error messages.
    :param text: Markdown body text with frontmatter already stripped.
    :return: List of error strings found during validation.
    """
    errors: list[str] = []
    non_fenced_text = remove_fenced_code_blocks(text)
    if collect_pattern_boundary_errors(
        non_fenced_text,
        RESEARCH_FORBIDDEN_HEADING_PATTERNS,
        lambda match_text: (
            f"FAIL [{file_path}]: RESEARCH.md must remain framework/library/topic "
            f"investigation, not {match_text}"
        ),
        errors,
    ):
        return errors
    if collect_pattern_boundary_errors(
        non_fenced_text,
        RESEARCH_FORBIDDEN_LABEL_PATTERNS,
        lambda match_text: (
            f"FAIL [{file_path}]: RESEARCH.md must remain framework/library/topic "
            f"investigation, not planning/audit content such as {match_text}"
        ),
        errors,
    ):
        return errors
    validate_research_prose_boundaries(file_path, non_fenced_text, errors)
    return errors


def validate_research_frontmatter_boundaries(
    file_path: str,
    frontmatter: dict[str, Any],
) -> list[str]:
    """Validates RESEARCH frontmatter fields against forbidden boundary patterns.

    Checks ``title``, ``description``, and ``subject.name`` fields for
    content that indicates project comparison, implementation planning,
    migration sequencing, or repository audit scope. Also validates that
    ``subject.name`` identifies an external subject rather than local
    repository context.

    Validation stops after collecting up to 3 errors.

    :param file_path: Path to the RESEARCH file for error messages.
    :param frontmatter: Parsed frontmatter dictionary.
    :return: List of error strings found during validation.
    """
    errors: list[str] = []
    for field in ("title", "description"):
        if validate_research_frontmatter_field(file_path, field, frontmatter.get(field), errors):
            return errors
    subject = frontmatter.get("subject")
    if subject is not None and isinstance(subject, dict) and not isinstance(subject, list):
        subject_name = subject.get("name")
        if isinstance(subject_name, str):
            validate_research_subject_name_boundary(file_path, subject_name, errors)
            if len(errors) >= 3:
                return errors
    return errors


def validate_research_positive_scope(
    file_path: str,
    text: str,
    frontmatter: dict[str, Any],
) -> list[str]:
    """Validates that the RESEARCH body references tokens from ``subject.name`` to maintain positive scope anchoring.

    When the frontmatter declares a ``subject.name``, this function checks
    that at least one token of length 4 or more from the normalized subject
    name appears in the body text (with fenced code blocks removed).
    This ensures the document stays anchored to its declared external
    investigation subject.

    :param file_path: Path to the RESEARCH file for error messages.
    :param text: Markdown body text with frontmatter already stripped.
    :param frontmatter: Parsed frontmatter dictionary.
    :return: List of error strings found during validation.
    """
    errors: list[str] = []
    subject = frontmatter.get("subject")
    if not (subject is not None and isinstance(subject, dict) and not isinstance(subject, list)):
        return errors
    subject_name = subject.get("name")
    if not isinstance(subject_name, str):
        return errors
    normalized_name = subject_name.strip().lower()
    if not normalized_name:
        return errors
    non_fenced_body = remove_fenced_code_blocks(text).lower()
    subject_tokens = [
        token
        for token in SUBJECT_TOKEN_SPLIT_RE.split(normalized_name)
        if len(token) >= 4
    ]
    if not subject_tokens:
        return errors
    if not any(token in non_fenced_body for token in subject_tokens):
        errors.append(
            f"FAIL [{file_path}]: RESEARCH.md body must reference subject.name "
            "to keep scope anchored to the investigated external subject"
        )
    return errors


def openapi_is_record(value: Any) -> bool:
    # :description: Checks if a value is a non-null, non-list dict-like object.
    # :param value: The value to check.
    # :return: True if the value is a dict and not None.
    return value is not None and isinstance(value, dict) and not isinstance(value, list)


def openapi_has_own(value: Any, key: str) -> bool:
    # :description: Checks if a dict-like object has an own property with the given key.
    # :param value: The object to inspect.
    # :param key: The property key to look up.
    # :return: True if the key exists as a direct own property.
    return openapi_is_record(value) and key in value


def openapi_parse_yaml_object(text: str) -> tuple[dict[str, Any] | None, str | None]:
    # :description: Parses YAML text into a dict, returning the result or an error message.
    # :param text: Raw YAML text to parse.
    # :return: Tuple of (parsed_dict, error_message). error_message is None on success.
    data, error = parse_yaml_document(text)
    if error is not None:
        return (None, error)
    if not openapi_is_record(data):
        return (None, "YAML document must be an object")
    return (data, None)


def openapi_extract_inline_token(line: str, start: int, end: int) -> str:
    # :description: Extracts the full token surrounding a match region within a line.
    # :param line: The source line containing the token.
    # :param start: Start index of the known match.
    # :param end: End index of the known match.
    # :return: The token string extended to word boundaries by whitespace.
    token_start = start
    while token_start > 0 and not OPENAPI_WHITESPACE_RE.match(line[token_start - 1]):
        token_start -= 1
    token_end = end
    while token_end < len(line) and not OPENAPI_WHITESPACE_RE.match(line[token_end]):
        token_end += 1
    return line[token_start:token_end]


def openapi_trim_chars(text: str, chars: str) -> str:
    # :description: Trims leading and trailing characters from a string.
    # :param text: The input string.
    # :param chars: Characters to trim from both ends.
    # :return: The trimmed string.
    s = 0
    e = len(text)
    while s < e and text[s] in chars:
        s += 1
    while e > s and text[e - 1] in chars:
        e -= 1
    return text[s:e]


def openapi_is_url_or_query_todo_marker(line: str, start: int, end: int) -> bool:
    # :description: Determines whether a TODO marker match is inside a URL or query parameter context.
    # :param line: The source line containing the marker.
    # :param start: Start index of the TODO marker match.
    # :param end: End index of the TODO marker match.
    # :return: True if the marker is part of a URL or query parameter.
    if start > 0 and line[start - 1] in ("=", "?", "&"):
        return True
    token = openapi_trim_chars(
        openapi_extract_inline_token(line, start, end),
        OPENAPI_TOKEN_WRAPPER_TRIM_CHARS,
    )
    if not token:
        return False
    if OPENAPI_URL_WITH_SCHEME_RE.match(token):
        return True
    if OPENAPI_RELATIVE_LINK_SCHEME_RE.match(token) and "//" not in token:
        return True
    return False


def openapi_has_unresolved_todo_marker(text: str) -> bool:
    # :description: Scans text for any unresolved TODO comment or inline markers.
    # :param text: The full file content to scan.
    # :return: True if at least one unresolved TODO marker is found.
    for line in LINE_SPLIT_RE.split(text):
        for pattern in OPENAPI_TODO_COMMENT_PATTERNS:
            if pattern.search(line):
                return True
        for match in OPENAPI_TEXT_TODO_MARKER_RE.finditer(line):
            if not openapi_is_url_or_query_todo_marker(line, match.start(), match.end()):
                return True
    return False


def openapi_has_unresolved_placeholder(text: str) -> bool:
    # :description: Scans text for unresolved template placeholder patterns like ``{{...}}``.
    # :param text: The full file content to scan.
    # :return: True if at least one placeholder pattern is found.
    return OPENAPI_PLACEHOLDER_RE.search(text) is not None


def resolve_openapi_schema(
    schema_obj: Any,
    openapi_doc: dict[str, Any],
) -> tuple[dict[str, Any] | None, str | None]:
    # :description: Resolves an OpenAPI schema object, following ``$ref`` pointers to ``components.schemas``.
    # :param schema_obj: The raw schema value from the OpenAPI document.
    # :param openapi_doc: The parsed OpenAPI document root object.
    # :return: Tuple of (resolved_schema, error_message).
    if not openapi_is_record(schema_obj):
        return (None, "schema must be an object")
    ref_value = schema_obj.get("$ref")
    if not isinstance(ref_value, str):
        return (schema_obj, None)
    match = OPENAPI_SCHEMA_REF_RE.match(ref_value.strip())
    if not match:
        return (None, "schema $ref must use '#/components/schemas/{name}'")
    components = openapi_doc.get("components")
    if not openapi_is_record(components):
        return (None, "components object is missing")
    schemas = components.get("schemas") if isinstance(components, dict) else None
    if not openapi_is_record(schemas):
        return (None, "components.schemas object is missing")
    schema_name = match.group("name") or ""
    resolved = schemas.get(schema_name) if isinstance(schemas, dict) else None
    if not openapi_is_record(resolved):
        return (None, f"components.schemas.{schema_name} is missing or invalid")
    return (resolved, None)


def collect_request_all_of_chain(
    schema_obj: dict[str, Any],
    openapi_doc: dict[str, Any],
    visited_schema_ids: set[int] | None = None,
) -> tuple[list[dict[str, Any]], str | None]:
    # :description: Collects the allOf composition chain for a request schema, resolving nested references.
    # :param schema_obj: The starting schema object.
    # :param openapi_doc: The parsed OpenAPI document root object.
    # :param visited_schema_ids: Set of object IDs already visited for cycle detection.
    # :return: Tuple of (chain_list, error_message).
    visited = visited_schema_ids if visited_schema_ids is not None else set()
    obj_id = id(schema_obj)
    if obj_id in visited:
        return ([], None)
    visited.add(obj_id)
    chain: list[dict[str, Any]] = [schema_obj]
    composition_value = schema_obj.get(OPENAPI_ALL_OF_COMPOSITION_KEY)
    if composition_value is None:
        return (chain, None)
    if not isinstance(composition_value, list) or len(composition_value) == 0:
        return (
            [],
            f"request schema {OPENAPI_ALL_OF_COMPOSITION_KEY} must be a non-empty array when present",
        )
    for index, nested_schema in enumerate(composition_value):
        if not openapi_is_record(nested_schema):
            return (
                [],
                f"request schema {OPENAPI_ALL_OF_COMPOSITION_KEY}[{index}] must be an object",
            )
        resolved_nested, nested_error = resolve_openapi_schema(nested_schema, openapi_doc)
        if resolved_nested is None:
            return (
                [],
                f"request schema {OPENAPI_ALL_OF_COMPOSITION_KEY}[{index}] is invalid: {nested_error}",
            )
        nested_chain, nested_chain_error = collect_request_all_of_chain(
            resolved_nested, openapi_doc, visited
        )
        if nested_chain_error:
            return ([], nested_chain_error)
        chain.extend(nested_chain)
    return (chain, None)


def validate_field_composition_value(
    field_name: str,
    composition_key: str,
    composition_value: Any,
) -> tuple[bool, str | None]:
    # :description: Validates that a field-level composition value conforms to OpenAPI rules.
    # :param field_name: Name of the parent field for error messages.
    # :param composition_key: The composition keyword being validated (allOf/oneOf/anyOf/not).
    # :param composition_value: The value associated with the composition key.
    # :return: Tuple of (is_valid, error_message).
    if composition_key == "not":
        if not openapi_is_record(composition_value) or len(composition_value) == 0:
            return (
                False,
                f"request schema field {field_name} not must be a non-empty object when present",
            )
        return (True, None)
    if not isinstance(composition_value, list) or len(composition_value) == 0:
        return (
            False,
            f"request schema field {field_name} {composition_key} must be a non-empty array when present",
        )
    for index, item in enumerate(composition_value):
        if not openapi_is_record(item):
            return (
                False,
                f"request schema field {field_name} {composition_key}[{index}] must be an object",
            )
    return (True, None)


def has_typed_required_field_schema(
    field_name: str,
    field_schema: Any,
) -> tuple[bool, str | None]:
    # :description: Checks whether a required field schema carries type information via type, $ref, or composition.
    # :param field_name: Name of the field for error messages.
    # :param field_schema: The schema object for the field.
    # :return: Tuple of (has_type_info, error_message).
    if not openapi_is_record(field_schema):
        return (False, f"request schema field {field_name} must be an object")
    field_type = field_schema.get("type")
    if isinstance(field_type, str) and field_type.strip():
        return (True, None)
    field_ref = field_schema.get("$ref")
    if isinstance(field_ref, str) and field_ref.strip():
        return (True, None)
    has_composition = False
    for comp_key in OPENAPI_FIELD_SCHEMA_COMPOSITION_KEYS:
        comp_value = field_schema.get(comp_key)
        if comp_value is None:
            continue
        is_valid_comp, comp_error = validate_field_composition_value(
            field_name, comp_key, comp_value
        )
        if not is_valid_comp:
            return (False, comp_error)
        if comp_error is not None:
            return (False, comp_error)
        has_composition = True
    return (has_composition, None)


def collect_request_required_fields(
    schema_chain: list[dict[str, Any]],
) -> tuple[set[str], str | None]:
    # :description: Collects the union of required field names across a schema chain.
    # :param schema_chain: List of schema objects in resolution order.
    # :return: Tuple of (required_field_set, error_message).
    required_fields: set[str] = set()
    for schema in schema_chain:
        required = schema.get("required")
        if required is None:
            continue
        if not isinstance(required, list):
            return (set(), "request schema required must be an array when present")
        for field_name in required:
            if not isinstance(field_name, str) or not field_name:
                return (
                    set(),
                    "request schema required entries must be non-empty strings",
                )
            required_fields.add(field_name)
    return (required_fields, None)


def collect_request_properties(
    schema_chain: list[dict[str, Any]],
) -> tuple[dict[str, list[Any]], str | None]:
    # :description: Collects properties across a schema chain, grouping by field name.
    # :param schema_chain: List of schema objects in resolution order.
    # :return: Tuple of (properties_map, error_message). Each value is a list of schema objects per field.
    properties: dict[str, list[Any]] = {}
    for schema in schema_chain:
        schema_properties = schema.get("properties")
        if schema_properties is None:
            continue
        if not openapi_is_record(schema_properties):
            return ({}, "request schema properties must be an object when present")
        for field_name, field_schema in schema_properties.items():
            if not field_name:
                continue
            if field_name not in properties:
                properties[field_name] = []
            properties[field_name].append(field_schema)
    return (properties, None)


def with_branch_label(branch_label: str, message: str) -> str:
    # :description: Prepends a branch label to an error message for nested schema validation context.
    # :param branch_label: The branch path label identifying the current validation location.
    # :param message: The base error message.
    # :return: The labeled error message.
    return f"{branch_label} {message}"


def validate_request_schema_required_fields(
    branch_label: str,
    required_fields: set[str],
    properties: dict[str, list[Any]],
    has_branch_composition: bool,
) -> str | None:
    # :description: Validates that every declared required field has a corresponding typed property definition.
    # :param branch_label: Label identifying the current branch for error messages.
    # :param required_fields: Set of required field names collected from the schema chain.
    # :param properties: Map of field names to their schema definitions.
    # :param has_branch_composition: Whether the local chain contains oneOf/anyOf composition.
    # :return: An error message string, or None if validation passes.
    if len(required_fields) == 0:
        return None if has_branch_composition else (
            f"{branch_label} request schema must declare non-empty required fields"
        )
    if len(properties) == 0:
        return (
            f"{branch_label} request schema must define properties "
            "or compose schemas that define properties"
        )
    for field_name in sorted(required_fields):
        if not field_name:
            return f"{branch_label} request schema required entries must be non-empty strings"
        field_schemas = properties.get(field_name)
        if field_schemas is None:
            return (
                f"{branch_label} request schema required fields must exist under "
                f"properties (missing: {field_name})"
            )
        has_typed_schema = False
        for field_schema in field_schemas:
            typed_schema, typed_error = has_typed_required_field_schema(
                field_name, field_schema
            )
            if typed_error:
                return with_branch_label(branch_label, typed_error)
            if typed_schema:
                has_typed_schema = True
        if not has_typed_schema:
            return (
                f"{branch_label} request schema required fields must define type "
                f"or use $ref/composition (missing typed schema: {field_name})"
            )
    return None


def validate_request_schema_composition_value(
    branch_label: str,
    composition_key: str,
    composition_value: Any,
) -> tuple[list[dict[str, Any]] | None, str | None]:
    # :description: Validates a branch composition value (oneOf/anyOf) and returns resolved nested schemas.
    # :param branch_label: Label for error messages.
    # :param composition_key: The composition keyword (oneOf or anyOf).
    # :param composition_value: The value to validate.
    # :return: Tuple of (nested_schemas_list_or_none, error_message).
    if not isinstance(composition_value, list) or len(composition_value) == 0:
        return (
            None,
            f"{branch_label} request schema {composition_key} must be a non-empty array when present",
        )
    nested_schemas: list[dict[str, Any]] = []
    for index, nested_schema in enumerate(composition_value):
        if not openapi_is_record(nested_schema):
            return (
                None,
                f"{branch_label} request schema {composition_key}[{index}] must be an object",
            )
        nested_schemas.append(nested_schema)
    return (nested_schemas, None)


def validate_nested_request_schema_variant(
    branch_label: str,
    composition_key: str,
    nested_index: int,
    nested_schema: dict[str, Any],
    openapi_doc: dict[str, Any],
    effective_chain: list[dict[str, Any]],
    active_schema_ids: set[int],
) -> str | None:
    # :description: Resolves and validates a single nested schema variant within a branch composition.
    # :param branch_label: Parent branch label for error messages.
    # :param composition_key: The composition keyword (oneOf or anyOf).
    # :param nested_index: Index of this variant within the composition array.
    # :param nested_schema: The raw nested schema object.
    # :param openapi_doc: The parsed OpenAPI document root object.
    # :param effective_chain: Accumulated schema chain including inherited entries.
    # :param active_schema_ids: Set of schema object IDs currently on the active stack.
    # :return: An error message string, or None if validation passes.
    resolved_nested, nested_error = resolve_openapi_schema(nested_schema, openapi_doc)
    if resolved_nested is None:
        return (
            f"{branch_label} request schema {composition_key}[{nested_index}] "
            f"is invalid: {nested_error}"
        )
    nested_label = f"{branch_label} {composition_key}[{nested_index}]"
    return validate_request_schema_variant(
        resolved_nested, openapi_doc, effective_chain, active_schema_ids, nested_label
    )


def validate_request_schema_branches(
    branch_label: str,
    local_chain: list[dict[str, Any]],
    openapi_doc: dict[str, Any],
    effective_chain: list[dict[str, Any]],
    active_schema_ids: set[int],
) -> str | None:
    # :description: Walks the local chain validating all branch composition variants recursively.
    # :param branch_label: Label for error messages.
    # :param local_chain: Schema chain collected from the current variant.
    # :param openapi_doc: The parsed OpenAPI document root object.
    # :param effective_chain: Full accumulated schema chain.
    # :param active_schema_ids: Set of schema object IDs currently on the active stack.
    # :return: An error message string, or None if all branches pass.
    for schema_entry in local_chain:
        for composition_key in OPENAPI_BRANCH_COMPOSITION_KEYS:
            composition_value = schema_entry.get(composition_key)
            if composition_value is None:
                continue
            nested_schemas, composition_error = validate_request_schema_composition_value(
                branch_label, composition_key, composition_value
            )
            if composition_error:
                return composition_error
            if nested_schemas is None:
                continue
            for index, nested_schema in enumerate(nested_schemas):
                nested_validation_error = validate_nested_request_schema_variant(
                    branch_label,
                    composition_key,
                    index,
                    nested_schema,
                    openapi_doc,
                    effective_chain,
                    active_schema_ids,
                )
                if nested_validation_error:
                    return nested_validation_error
    return None


def validate_request_schema_variant(
    schema_obj: dict[str, Any],
    openapi_doc: dict[str, Any],
    inherited_chain: list[dict[str, Any]],
    active_schema_ids: set[int],
    branch_label: str,
) -> str | None:
    # :description: Validates a single request schema variant including its allOf chain, required fields, and branches.
    # :param schema_obj: The resolved schema object to validate.
    # :param openapi_doc: The parsed OpenAPI document root object.
    # :param inherited_chain: Schema chain inherited from parent variants.
    # :param active_schema_ids: Set of schema object IDs currently on the active stack for cycle detection.
    # :param branch_label: Label identifying this variant for error messages.
    # :return: An error message string, or None if validation passes.
    obj_id = id(schema_obj)
    if obj_id in active_schema_ids:
        return None
    next_active_schema_ids = set(active_schema_ids)
    next_active_schema_ids.add(obj_id)
    local_chain, chain_error = collect_request_all_of_chain(
        schema_obj, openapi_doc, set()
    )
    if chain_error:
        return with_branch_label(branch_label, chain_error)
    effective_chain = inherited_chain + local_chain
    required_fields, required_error = collect_request_required_fields(effective_chain)
    if required_error:
        return with_branch_label(branch_label, required_error)
    properties, properties_error = collect_request_properties(effective_chain)
    if properties_error:
        return with_branch_label(branch_label, properties_error)
    has_branch_composition = any(
        any(
            schema_entry.get(comp_key) is not None
            for comp_key in OPENAPI_BRANCH_COMPOSITION_KEYS
        )
        for schema_entry in local_chain
    )
    required_field_error = validate_request_schema_required_fields(
        branch_label, required_fields, properties, has_branch_composition
    )
    if required_field_error:
        return required_field_error
    return validate_request_schema_branches(
        branch_label, local_chain, openapi_doc, effective_chain, next_active_schema_ids
    )


def validate_required_typed_request_schema(
    schema_obj: dict[str, Any],
    openapi_doc: dict[str, Any],
) -> str | None:
    # :description: Entry point for validating that a request body schema declares typed required fields.
    # :param schema_obj: The raw schema object (may contain $ref).
    # :param openapi_doc: The parsed OpenAPI document root object.
    # :return: An error message string, or None if validation passes.
    resolved_schema, resolve_error = resolve_openapi_schema(schema_obj, openapi_doc)
    if resolved_schema is None:
        return resolve_error or "request schema is invalid"
    return validate_request_schema_variant(
        resolved_schema, openapi_doc, [], set(), "request schema"
    )


def resolve_openapi_component(
    entry: Any,
    openapi_doc: dict[str, Any],
    component_key: str,
    ref_pattern: re.Pattern[str],
) -> tuple[dict[str, Any] | None, str | None]:
    # :description: Resolves an OpenAPI component reference (requestBody, response, etc.) against the document.
    # :param entry: The raw entry value, which may be an inline object or a $ref pointer.
    # :param openapi_doc: The parsed OpenAPI document root object.
    # :param component_key: The component map key under components (e.g. ``requestBodies``, ``responses``).
    # :param ref_pattern: Compiled regex that matches valid $ref values for this component type.
    # :return: Tuple of (resolved_component, error_message).
    if not openapi_is_record(entry):
        return (None, "must be an object")
    ref_value = entry.get("$ref")
    if not isinstance(ref_value, str):
        return (entry, None)
    match = ref_pattern.match(ref_value.strip())
    if not match:
        return (None, f"$ref must use '#/components/{component_key}/{{name}}'")
    components = openapi_doc.get("components")
    if not openapi_is_record(components):
        return (None, "components object is missing")
    component_map = components.get(component_key) if isinstance(components, dict) else None
    if not openapi_is_record(component_map):
        return (None, f"components.{component_key} object is missing")
    component_name = match.group("name") or ""
    resolved = component_map.get(component_name) if isinstance(component_map, dict) else None
    if not openapi_is_record(resolved):
        return (None, f"components.{component_key}.{component_name} is missing or invalid")
    return (resolved, None)


def normalize_openapi_status_key(
    status_code: Any,
) -> tuple[str | None, str | None]:
    # :description: Normalizes an OpenAPI response status key to its canonical string form.
    # :param status_code: The raw status key value (int or string).
    # :return: Tuple of (normalized_status_string, error_message).
    if isinstance(status_code, int):
        if 100 <= status_code <= 599:
            return (str(status_code), None)
        return (None, "status key must be `default`, `NNN`, or `NXX`")
    if not isinstance(status_code, str):
        return (None, "status key must be `default`, `NNN`, or `NXX`")
    normalized = status_code.strip()
    if normalized == OPENAPI_STATUS_DEFAULT:
        return (OPENAPI_STATUS_DEFAULT, None)
    normalized_upper = normalized.upper()
    if OPENAPI_STATUS_CODE_RE.match(normalized_upper):
        return (normalized_upper, None)
    if OPENAPI_STATUS_RANGE_RE.match(normalized_upper):
        return (normalized_upper, None)
    return (None, "status key must be `default`, `NNN`, or `NXX`")


def openapi_response_allows_no_body(method: str, status_code: Any) -> bool:
    # :description: Determines whether an HTTP response for the given method and status code may omit a body.
    # :param method: The HTTP method name (e.g. ``get``, ``post``).
    # :param status_code: The response status code (int or string).
    # :return: True if the response may legitimately have no content/body.
    normalized_method = method.strip().lower()
    if normalized_method == "head":
        return True
    if isinstance(status_code, int):
        return (100 <= status_code <= 199) or status_code in OPENAPI_NO_BODY_STATUS_CODES
    if not isinstance(status_code, str):
        return False
    normalized_status = status_code.strip().upper()
    if OPENAPI_STATUS_CODE_RE.match(normalized_status):
        numeric_status = int(normalized_status)
        return (100 <= numeric_status <= 199) or numeric_status in OPENAPI_NO_BODY_STATUS_CODES
    if OPENAPI_STATUS_RANGE_RE.match(normalized_status):
        return normalized_status.startswith("1")
    return False


def validate_openapi_request_body_content(
    openapi_file: str,
    openapi_doc: dict[str, Any],
    operation_label: str,
    content: dict[str, Any],
) -> list[str]:
    # :description: Validates each media-type entry inside a requestBody content object.
    # :param openapi_file: Path to the OpenAPI file for error messages.
    # :param openapi_doc: The parsed OpenAPI document root object.
    # :param operation_label: Human-readable label for the current operation.
    # :param content: The content map from the requestBody definition.
    # :return: List of error strings found during validation.
    errors: list[str] = []
    for media_type, media_value in content.items():
        if not openapi_is_record(media_value):
            errors.append(
                f"FAIL [{openapi_file}]: requestBody.content.{media_type} "
                f"must be an object in {operation_label}"
            )
            continue
        schema = media_value.get("schema")
        if not openapi_is_record(schema):
            errors.append(
                f"FAIL [{openapi_file}]: requestBody.content.{media_type}.schema "
                f"is required in {operation_label}"
            )
            continue
        schema_error = validate_required_typed_request_schema(schema, openapi_doc)
        if schema_error:
            errors.append(
                f"FAIL [{openapi_file}]: Invalid request schema in {operation_label}: {schema_error}"
            )
    return errors


def validate_openapi_request_body(
    openapi_file: str,
    openapi_doc: dict[str, Any],
    operation_label: str,
    operation: dict[str, Any],
) -> list[str]:
    # :description: Validates the requestBody of an OpenAPI operation, resolving $ref and checking content schemas.
    # :param openapi_file: Path to the OpenAPI file for error messages.
    # :param openapi_doc: The parsed OpenAPI document root object.
    # :param operation_label: Human-readable label for the current operation.
    # :param operation: The operation object from the paths entry.
    # :return: List of error strings found during validation.
    if "requestBody" not in operation:
        return []
    request_body = operation["requestBody"]
    if request_body is None:
        return [
            f"FAIL [{openapi_file}]: requestBody must not be null in {operation_label}"
        ]
    resolved_request_body, request_body_error = resolve_openapi_component(
        request_body, openapi_doc, "requestBodies", OPENAPI_REQUEST_BODY_REF_RE
    )
    if resolved_request_body is None:
        return [
            f"FAIL [{openapi_file}]: Invalid requestBody in {operation_label}: {request_body_error}"
        ]
    content = resolved_request_body.get("content") if isinstance(resolved_request_body, dict) else None
    if not isinstance(content, dict) or len(content) == 0:
        return [
            f"FAIL [{openapi_file}]: requestBody.content must be a non-empty object in {operation_label}"
        ]
    return validate_openapi_request_body_content(
        openapi_file, openapi_doc, operation_label, content
    )


def validate_openapi_response_content(
    openapi_file: str,
    openapi_doc: dict[str, Any],
    operation_label: str,
    status_code: str,
    content: dict[str, Any],
) -> list[str]:
    # :description: Validates each media-type entry inside a response content object.
    # :param openapi_file: Path to the OpenAPI file for error messages.
    # :param openapi_doc: The parsed OpenAPI document root object.
    # :param operation_label: Human-readable label for the current operation.
    # :param status_code: The response status code string.
    # :param content: The content map from the response definition.
    # :return: List of error strings found during validation.
    errors: list[str] = []
    has_schema = False
    for media_type, media_value in content.items():
        if not openapi_is_record(media_value):
            errors.append(
                f"FAIL [{openapi_file}]: response {status_code} content {media_type} "
                f"must be an object in {operation_label}"
            )
            continue
        schema = media_value.get("schema")
        if not openapi_is_record(schema):
            errors.append(
                f"FAIL [{openapi_file}]: response {status_code} content {media_type} "
                f"schema must be an object in {operation_label}"
            )
            continue
        resolved_schema, schema_error = resolve_openapi_schema(schema, openapi_doc)
        if resolved_schema is None:
            errors.append(
                f"FAIL [{openapi_file}]: Invalid response schema in {operation_label} "
                f"for {status_code} {media_type}: {schema_error}"
            )
            continue
        has_schema = True
    if not has_schema:
        errors.append(
            f"FAIL [{openapi_file}]: response {status_code} must define at least "
            f"one content schema in {operation_label}"
        )
    return errors


def validate_openapi_response_entry(
    openapi_file: str,
    openapi_doc: dict[str, Any],
    operation_label: str,
    method: str,
    status_code: str,
    response_value: Any,
) -> list[str]:
    # :description: Validates a single response entry under an operation's responses map.
    # :param openapi_file: Path to the OpenAPI file for error messages.
    # :param openapi_doc: The parsed OpenAPI document root object.
    # :param operation_label: Human-readable label for the current operation.
    # :param method: The HTTP method name.
    # :param status_code: The raw status key string from the responses map.
    # :param response_value: The response value (may be inline or a $ref).
    # :return: List of error strings found during validation.
    errors: list[str] = []
    normalized_status_code, status_key_error = normalize_openapi_status_key(status_code)
    if normalized_status_code is None:
        return [
            f"FAIL [{openapi_file}]: Invalid response status key "
            f"{repr(status_code)} in {operation_label}: {status_key_error}"
        ]
    resolved_response, response_error = resolve_openapi_component(
        response_value, openapi_doc, "responses", OPENAPI_RESPONSE_REF_RE
    )
    if resolved_response is None:
        return [
            f"FAIL [{openapi_file}]: Invalid response {status_code} in {operation_label}: {response_error}"
        ]
    content = resolved_response.get("content")
    if content is None:
        if not openapi_response_allows_no_body(method, normalized_status_code):
            errors.append(
                f"FAIL [{openapi_file}]: response {status_code} must define "
                f"content with schema in {operation_label}"
            )
        return errors
    if not openapi_is_record(content):
        return [
            f"FAIL [{openapi_file}]: response {status_code} content must be "
            f"an object in {operation_label}"
        ]
    if len(content) == 0:
        if not openapi_response_allows_no_body(method, normalized_status_code):
            errors.append(
                f"FAIL [{openapi_file}]: response {status_code} must define "
                f"content with schema in {operation_label}"
            )
        return errors
    return validate_openapi_response_content(
        openapi_file, openapi_doc, operation_label, status_code, content
    )


def validate_openapi_responses(
    openapi_file: str,
    openapi_doc: dict[str, Any],
    operation_label: str,
    method: str,
    operation: dict[str, Any],
) -> list[str]:
    # :description: Validates the responses object of an OpenAPI operation.
    # :param openapi_file: Path to the OpenAPI file for error messages.
    # :param openapi_doc: The parsed OpenAPI document root object.
    # :param operation_label: Human-readable label for the current operation operation.
    # :param method: The HTTP method name.
    # :param operation: The operation object from the paths entry.
    # :return: List of error strings found during validation.
    responses = operation.get("responses") if isinstance(operation, dict) else None
    if not isinstance(responses, dict) or len(responses) == 0:
        return [
            f"FAIL [{openapi_file}]: responses must be a non-empty object in {operation_label}"
        ]
    errors: list[str] = []
    for sc, response_value in responses.items():
        errors.extend(
            validate_openapi_response_entry(
                openapi_file, openapi_doc, operation_label, method, sc, response_value
            )
        )
    return errors


def validate_openapi_operation(
    openapi_file: str,
    openapi_doc: dict[str, Any],
    path_key: str,
    method: str,
    operation: Any,
) -> list[str]:
    # :description: Validates a single OpenAPI operation (requestBody + responses).
    # :param openapi_file: Path to the OpenAPI file for error messages.
    # :param openapi_doc: The parsed OpenAPI document root object.
    # :param path_key: The path key string (e.g. ``/users/{id}``).
    # :param method: The lowercase HTTP method name.
    # :param operation: The raw operation value from the paths entry.
    # :return: List of error strings found during validation.
    operation_label = f"{path_key} {method.upper()}"
    if not openapi_is_record(operation):
        return [f"FAIL [{openapi_file}]: Operation must be an object: {operation_label}"]
    return [
        *validate_openapi_request_body(openapi_file, openapi_doc, operation_label, operation),
        *validate_openapi_responses(openapi_file, openapi_doc, operation_label, method, operation),
    ]


def validate_openapi_unresolved_markers(openapi_file: str) -> list[str]:
    """Validates an OpenAPI YAML file for unresolved TODO comment markers and template placeholders.

    :param openapi_file: Path to the OpenAPI YAML file to validate.
    :return: List of error strings. Empty list means no issues were found.
    """
    text = Path(openapi_file).read_text(encoding="utf-8")
    errors: list[str] = []
    if openapi_has_unresolved_todo_marker(text):
        errors.append(f"FAIL [{openapi_file}]: Unresolved TODO markers found")
    if openapi_has_unresolved_placeholder(text):
        errors.append(f"FAIL [{openapi_file}]: Unresolved template placeholders found")
    return errors


def validate_openapi_minimal_structure(openapi_file: str) -> list[str]:
    """Validates that an OpenAPI YAML file has minimal required structure including request/response schemas.

    This checks that the file parses as valid YAML with a top-level object,
    contains a non-empty ``paths`` object with at least one HTTP operation,
    and that each operation defines valid request body content schemas
    and response content schemas where applicable.

    :param openapi_file: Path to the OpenAPI YAML file to validate.
    :return: List of error strings. Empty list means the structure is valid.
    """
    text = Path(openapi_file).read_text(encoding="utf-8")
    openapi_doc, parse_error = openapi_parse_yaml_object(text)
    if openapi_doc is None:
        return [f"FAIL [{openapi_file}]: {parse_error or 'Invalid YAML'}"]
    errors: list[str] = []
    paths = openapi_doc.get("paths") if isinstance(openapi_doc, dict) else None
    if not isinstance(paths, dict) or len(paths) == 0:
        errors.append(
            f"FAIL [{openapi_file}]: paths must be a non-empty object for OpenAPI validation"
        )
        return errors
    operation_count = 0
    for path_key, path_item in paths.items():
        if not openapi_is_record(path_item):
            errors.append(f"FAIL [{openapi_file}]: paths.{path_key} must be an object")
            continue
        for method, operation in path_item.items():
            normalized_method = method.lower().strip()
            if normalized_method not in OPENAPI_HTTP_METHODS:
                continue
            operation_count += 1
            errors.extend(
                validate_openapi_operation(
                    openapi_file, openapi_doc, str(path_key), normalized_method, operation
                )
            )
    if operation_count == 0:
        errors.append(
            f"FAIL [{openapi_file}]: paths must include at least one HTTP operation"
        )
    return errors


def extract_inline_token_validation(
    line: str,
    start: int,
    end: int,
) -> str:
    # :description: Expands a match range to capture the full surrounding token.
    # :param line: The source line.
    # :param start: Start index of the match.
    # :param end: End index of the match.
    # :return: The expanded token substring.
    token_start = start
    while (
        token_start > 0
        and not WHITESPACE_RE.match(line[token_start - 1])
    ):
        token_start -= 1
    token_end = end
    while token_end < len(line) and not WHITESPACE_RE.match(line[token_end]):
        token_end += 1
    return line[token_start:token_end]


def trim_chars_validation(text: str, chars: str) -> str:
    # :description: Strips leading and trailing characters from a string.
    # :param text: Input string.
    # :param chars: Characters to strip.
    # :return: Trimmed string.
    s = 0
    e = len(text)
    while s < e and text[s] in chars:
        s += 1
    while e > s and text[e - 1] in chars:
        e -= 1
    return text[s:e]


def is_url_or_query_todo_marker_validation(
    line: str,
    start: int,
    end: int,
) -> bool:
    # :description: Determines whether a TODO marker match sits inside a URL or query parameter.
    # :param line: The source line containing the match.
    # :param start: Match start index.
    # :param end: Match end index.
    # :return: True if the match is part of a URL/query string.
    if start > 0 and line[start - 1] in ("=", "?", "&"):
        return True
    token = trim_chars_validation(
        extract_inline_token_validation(line, start, end),
        TOKEN_WRAPPER_TRIM_CHARS,
    )
    return bool(token and URL_WITH_SCHEME_RE.match(token))


def is_iso_8601_date(value: Any) -> bool:
    # :description: Validates that a value is a real ISO 8601 calendar date (YYYY-MM-DD).
    # :param value: Value to check.
    # :return: True if value is a valid ISO 8601 date string.
    if not isinstance(value, str):
        return False
    text = value.strip()
    if not ISO_8601_DATE_RE.match(text):
        return False
    year_text, month_text, day_text = text.split("-")
    year = int(year_text)
    month = int(month_text)
    day = int(day_text)
    try:
        parsed = datetime.date(year, month, day)
    except ValueError:
        return False
    return (
        parsed.year == year
        and parsed.month == month
        and parsed.day == day
    )


def build_schema_error_message(
    file_path: str,
    schema_name: str,
    validation_errors: list[dict] | None,
) -> str:
    # :description: Builds a human-readable schema validation error message.
    # :param file_path: Path of the validated file.
    # :param schema_name: Basename of the schema file.
    # :param validation_errors: jsonschema validation error list.
    # :return: Formatted error string.
    if not validation_errors:
        return f"FAIL [{file_path}]: Frontmatter does not match schema {schema_name}"
    first = validation_errors[0]
    keyword = str(first.get("keyword", ""))
    message = str(first.get("message", "schema validation failed")).strip()
    instance_path = (
        str(first.get("instancePath", "")).strip() or "/"
    )
    detail = (
        f"FAIL [{file_path}]: Frontmatter does not match schema "
        f"{schema_name} at {instance_path}: {message}"
    )
    if keyword != "additionalProperties":
        return detail
    props: list[str] = []
    for err in validation_errors:
        if str(err.get("keyword", "")) != "additionalProperties":
            continue
        params = err.get("params") or {}
        prop = params.get("additionalProperty")
        if isinstance(prop, str) and prop and prop not in props:
            props.append(prop)
    is_research = "research" in schema_name.lower()
    if is_research:
        suffix = (
            f" Additional field(s): {', '.join(props)}."
            if props
            else ""
        )
        return (
            f"{detail}.{suffix} "
            "RESEARCH frontmatter does not allow additional fields. Remove unknown field(s)."
        )
    if props:
        props_text = ", ".join(props)
        return (
            f"{detail}. Additional field(s): {props_text}. "
            "Move custom fields under frontmatter `metadata` "
            "(for example, `metadata.<field_name>`)."
        )
    return (
        f"{detail}. "
        "Move custom fields under frontmatter `metadata` "
        "(for example, `metadata.<field_name>`)."
    )


def parse_frontmatter_dict(file_path: str) -> tuple[Any | None, str | None]:
    """Parses and validates YAML frontmatter object from a Markdown file.

    :param file_path: Path to the Markdown file.
    :return: Tuple of (parsed_dict_or_None, error_message_or_None).
    """
    try:
        block = extract_frontmatter_from_file(file_path)
        frontmatter = block.yaml if block is not None else None
        if frontmatter is None or not frontmatter.strip():
            return (None, f"FAIL [{file_path}]: No YAML frontmatter found")
        data, error = parse_yaml_document(f"{frontmatter}\n")
        if error is not None:
            return (None, error)
        if not (data is not None and isinstance(data, dict) and not isinstance(data, list)):
            msg = (
                "YAML frontmatter must be an object"
                if data is not None
                else "Invalid YAML frontmatter"
            )
            return (None, msg)
        return (data, None)
    except Exception as exc:
        return (None, f"FAIL [{file_path}]: {exc}")


def validate_frontmatter_schema(
    file_path: str,
    schema_path: str,
    validator: Any,
    frontmatter_dict: Any,
) -> tuple[bool, str | None]:
    """Validates a frontmatter dict against a compiled JSON Schema validator.

    :param file_path: Path of the file being validated (for error messages).
    :param schema_path: Path to the schema file (basename used in messages).
    :param validator: Compiled jsonschema Draft7Validator instance.
    :param frontmatter_dict: Parsed frontmatter dictionary.
    :return: Tuple of (is_valid, error_message_or_None).
    """
    import jsonschema
    errors = sorted(validator.iter_errors(frontmatter_dict), key=lambda e: list(e.path))
    if not errors:
        return (True, None)
    error_dicts = [
        {
            "keyword": err.validator,
            "message": err.message,
            "instancePath": "/" + "/".join(str(p) for p in err.absolute_path)
            if err.absolute_path
            else "/",
            "params": getattr(err, "params", {}),
        }
        for err in errors
    ]
    return (False, build_schema_error_message(file_path, schema_path, error_dicts))


def validate_last_updated(
    file_path: str,
    frontmatter: Any,
) -> str | None:
    """Validates optional last_updated frontmatter date format.

    :param file_path: Path of the file being validated.
    :param frontmatter: Parsed frontmatter dictionary.
    :return: Error string if invalid, None if valid or absent.
    """
    value = frontmatter.get("last_updated") if isinstance(frontmatter, dict) else None
    if value is None or is_iso_8601_date(value):
        return None
    return (
        f"FAIL [{file_path}]: last_updated must be an ISO 8601 calendar date "
        "(YYYY-MM-DD, for example, 2026-03-02)"
    )


def validate_markdownlint_inline_directives(
    file_path: str,
    text: str,
) -> list[str]:
    """Validates that inline markdownlint directives are prohibited.

    :param file_path: Path of the file being validated.
    :param text: Markdown body text to scan.
    :return: List of error strings (empty if no violations).
    """
    cleaned = remove_fenced_code_blocks(text)
    match = MARKDOWNLINT_INLINE_DIRECTIVE_RE.search(cleaned)
    if match:
        return [
            f"FAIL [{file_path}]: Inline markdownlint directives are prohibited: "
            f"{match.group(0).strip()}"
        ]
    return []


def validate_manual_numbered_headings(
    file_path: str,
    text: str,
) -> list[str]:
    """Validates that manual numeric heading prefixes are not used.

    :param file_path: Path of the file being validated.
    :param text: Markdown body text to scan.
    :return: List of error strings (up to 3, empty if no violations).
    """
    errors: list[str] = []
    for line in LINE_SPLIT_RE.split(remove_fenced_code_blocks(text)):
        if MANUAL_NUMBERED_HEADING_RE.match(line):
            errors.append(
                f"FAIL [{file_path}]: Manual numbered headings are prohibited: "
                f"{line.strip()}"
            )
            if len(errors) >= 3:
                break
    return errors


def has_unresolved_todo_marker(text: str) -> bool:
    """Checks for unresolved TODO comment markers in text.

    Scans for HTML comments, block comments, JS line comments,
    hash comments, double-dash comments, and bare ``todo:`` text patterns
    (excluding matches inside URLs).

    :param text: Non-fenced code-block text to scan.
    :return: True if any TODO marker is found.
    """
    for line in LINE_SPLIT_RE.split(text):
        for pattern in TODO_COMMENT_PATTERNS:
            if pattern.search(line):
                return True
        for m in TEXT_TODO_MARKER_RE.finditer(line):
            if not is_url_or_query_todo_marker_validation(line, m.start(), m.end()):
                return True
    return False


def has_unresolved_placeholder(text: str) -> bool:
    """Checks for unresolved ``{{...}}`` template placeholder markers.

    :param text: Non-fenced code-block text to scan.
    :return: True if any placeholder is found.
    """
    return bool(PLACEHOLDER_RE.search(text))


def collect_text_marker_errors(
    file_path: str,
    non_fenced_text: str,
) -> list[str]:
    """Collects unresolved TODO and placeholder marker failures.

    :param file_path: Path of the file being validated.
    :param non_fenced_text: Text with fenced code blocks already removed.
    :return: List of error strings for each type of unresolved marker found.
    """
    errors: list[str] = []
    if has_unresolved_todo_marker(non_fenced_text):
        errors.append(f"FAIL [{file_path}]: Unresolved TODO markers found")
    if has_unresolved_placeholder(non_fenced_text):
        errors.append(f"FAIL [{file_path}]: Unresolved template placeholders found")
    return errors


def push_errors(target: list[str], source: list[str]) -> None:
    """Appends all errors from source into target.

    :param target: Destination error list to extend.
    :param source: Source error list whose items are appended.
    """
    target.extend(source)


def validate_spec_file(
    file_path: str,
    spec_schema: str,
    spec_validator: Any,
    spec_root: str,
) -> FileValidationResult:
    """Validates one SPEC.md file with schema, content, and lint checks.

    Runs frontmatter parsing, schema validation, date format checking,
    link validation, content lint rules, scaffolding detection, and
    adjacent OpenAPI file validation.

    :param file_path: Absolute path to the SPEC.md file.
    :param spec_schema: Path to the SPEC JSON Schema file.
    :param spec_validator: Pre-compiled jsonschema Draft7Validator for SPEC.
    :param spec_root: Root directory of the spec repository.
    :return: FileValidationResult with collected errors and pass/fail status.
    """
    errors: list[str] = []
    frontmatter_dict, parse_message = parse_frontmatter_dict(file_path)
    if frontmatter_dict is None:
        if parse_message:
            errors.append(parse_message)
    else:
        schema_ok, schema_message = validate_frontmatter_schema(
            file_path, spec_schema, spec_validator, frontmatter_dict
        )
        if not schema_ok and schema_message:
            errors.append(schema_message)
        date_error = validate_last_updated(file_path, frontmatter_dict)
        if date_error:
            errors.append(date_error)
        push_errors(
            errors,
            validate_frontmatter_links(file_path, frontmatter_dict, spec_root),
        )
    with open(file_path, "r", encoding="utf-8") as f:
        text = f.read()
    body_text = extract_markdown_body(text)
    push_errors(errors, validate_no_reverse_direction_points(file_path, body_text))
    push_errors(errors, validate_deprecated_link_sections(file_path, body_text))
    push_errors(errors, validate_markdownlint_inline_directives(file_path, body_text))
    push_errors(errors, validate_manual_numbered_headings(file_path, body_text))
    push_errors(
        errors,
        collect_text_marker_errors(file_path, remove_fenced_code_blocks(body_text)),
    )
    scaffold_hits = find_unresolved_spec_scaffolding(text)
    for line in scaffold_hits:
        errors.append(
            f"FAIL [{file_path}]: Unresolved SPEC scaffolding instruction found: {line}"
        )
    openapi_file = os.path.join(os.path.dirname(file_path), "openapi.yaml")
    if os.path.exists(openapi_file):
        if os.path.isfile(openapi_file):
            push_errors(errors, validate_openapi_unresolved_markers(openapi_file))
            push_errors(errors, validate_openapi_minimal_structure(openapi_file))
        else:
            errors.append(
                f"FAIL [{openapi_file}]: OpenAPI path exists but is not a file"
            )
    return FileValidationResult(errors=errors, passed=len(errors) == 0)


def validate_research_file(
    file_path: str,
    research_schema: str,
    research_validator: Any,
) -> FileValidationResult:
    """Validates one RESEARCH.md file with schema, scope, and lint checks.

    Runs location validation, frontmatter parsing, schema validation,
    date format, subject URL, scope boundaries, positive scope, and
    content lint rules.

    :param file_path: Absolute path to the RESEARCH.md file.
    :param research_schema: Path to the RESEARCH JSON Schema file.
    :param research_validator: Pre-compiled jsonschema Draft7Validator for RESEARCH.
    :return: FileValidationResult with collected errors and pass/fail status.
    """
    errors: list[str] = []
    location_ok, location_message = validate_research_location(file_path)
    if not location_ok and location_message:
        errors.append(location_message)
    frontmatter_dict, parse_message = parse_frontmatter_dict(file_path)
    if frontmatter_dict is None:
        if parse_message:
            errors.append(parse_message)
    else:
        schema_ok, schema_message = validate_frontmatter_schema(
            file_path, research_schema, research_validator, frontmatter_dict
        )
        if not schema_ok and schema_message:
            errors.append(schema_message)
        date_error = validate_last_updated(file_path, frontmatter_dict)
        if date_error:
            errors.append(date_error)
        subject_url_error = validate_research_subject_url(
            file_path, frontmatter_dict
        )
        if subject_url_error:
            errors.append(subject_url_error)
        push_errors(
            errors,
            validate_research_frontmatter_boundaries(file_path, frontmatter_dict),
        )
    with open(file_path, "r", encoding="utf-8") as f:
        text = f.read()
    body_text = extract_markdown_body(text)
    push_errors(errors, validate_markdownlint_inline_directives(file_path, body_text))
    push_errors(errors, validate_manual_numbered_headings(file_path, body_text))
    push_errors(errors, validate_research_scope_boundaries(file_path, body_text))
    push_errors(
        errors,
        validate_research_positive_scope(
            file_path, body_text, frontmatter_dict if frontmatter_dict is not None else {}
        ),
    )
    push_errors(
        errors,
        collect_text_marker_errors(file_path, remove_fenced_code_blocks(body_text)),
    )
    return FileValidationResult(errors=errors, passed=len(errors) == 0)


def validate_contract_file(
    file_path: str,
    contract_schema: str,
    contract_validator: Any,
) -> FileValidationResult:
    """Validates one CONTRACT.md file with schema, structure, and lint checks.

    Runs frontmatter parsing, schema validation, date format, contract
    units structure validation, and content lint rules.

    :param file_path: Absolute path to the CONTRACT file.
    :param contract_schema: Path to the CONTRACT JSON Schema file.
    :param contract_validator: Pre-compiled jsonschema Draft7Validator for CONTRACT.
    :return: FileValidationResult with collected errors and pass/fail status.
    """
    errors: list[str] = []
    frontmatter_dict, parse_message = parse_frontmatter_dict(file_path)
    if frontmatter_dict is None:
        if parse_message:
            errors.append(parse_message)
    else:
        schema_ok, schema_message = validate_frontmatter_schema(
            file_path, contract_schema, contract_validator, frontmatter_dict
        )
        if not schema_ok and schema_message:
            errors.append(schema_message)
        date_error = validate_last_updated(file_path, frontmatter_dict)
        if date_error:
            errors.append(date_error)
    with open(file_path, "r", encoding="utf-8") as f:
        text = f.read()
    body_text = extract_markdown_body(text)
    push_errors(errors, validate_markdownlint_inline_directives(file_path, body_text))
    push_errors(errors, validate_contract_units_structure(file_path, body_text))
    push_errors(
        errors,
        collect_text_marker_errors(file_path, remove_fenced_code_blocks(body_text)),
    )
    return FileValidationResult(errors=errors, passed=len(errors) == 0)


def validate_changelog_file(root_changelog_file: str) -> FileValidationResult:
    """Validates the root CHANGELOG.md document.

    Runs text marker detection, markdownlint directive checks, entry heading
    presence verification, and entry order validation.

    :param root_changelog_file: Absolute path to the root CHANGELOG file.
    :return: FileValidationResult with collected errors and pass/fail status.
    """
    errors: list[str] = []
    with open(root_changelog_file, "r", encoding="utf-8") as f:
        text = f.read()
    push_errors(
        errors,
        collect_text_marker_errors(
            root_changelog_file, remove_fenced_code_blocks(text)
        ),
    )
    push_errors(
        errors,
        validate_markdownlint_inline_directives(root_changelog_file, text),
    )
    if not has_changelog_entry_heading(text):
        errors.append(
            f"FAIL [{root_changelog_file}]: Root CHANGELOG must include at least one "
            "entry heading in format '## YYYY-MM-DD - ...'"
        )
    push_errors(
        errors,
        validate_changelog_entry_order(root_changelog_file, text),
    )
    return FileValidationResult(errors=errors, passed=len(errors) == 0)


__all__ = [
    "FrontmatterBlock",
    "FileValidationResult",
    "LINE_SPLIT_RE",
    "collect_files_by_name",
    "extract_frontmatter_from_file",
    "extract_frontmatter_from_text",
    "extract_markdown_body",
    "find_spec_root",
    "find_unresolved_spec_scaffolding",
    "has_changelog_entry_heading",
    "list_by_basename",
    "parse_frontmatter_dict",
    "parse_yaml_document",
    "push_errors",
    "remove_fenced_code_blocks",
    "resolve_validation_roots",
    "validate_changelog_entry_order",
    "validate_changelog_file",
    "validate_changelog_layout",
    "validate_contract_file",
    "validate_contract_units_structure",
    "validate_deprecated_link_sections",
    "validate_frontmatter_links",
    "validate_frontmatter_schema",
    "validate_last_updated",
    "validate_manual_numbered_headings",
    "validate_markdownlint_inline_directives",
    "validate_openapi_minimal_structure",
    "validate_openapi_unresolved_markers",
    "validate_research_file",
    "validate_research_frontmatter_boundaries",
    "validate_research_location",
    "validate_research_positive_scope",
    "validate_research_scope_boundaries",
    "validate_research_subject_url",
    "validate_spec_file",
    "collect_text_marker_errors",
    "escape_reg_exp",
    "to_posix_path_for_validation",
    "generate_mermaid",
]

STYLE_MAP: dict[str, str] = {
    "draft": "draft",
    "review": "review",
    "approved": "approved",
    "wip": "wip",
    "implemented": "implemented",
    "deprecated": "deprecated",
    "superseded": "superseded",
    "removed": "removed",
}
CLASS_DEFS: list[str] = [
    "classDef draft fill:#f9f9f9,stroke:#999",
    "classDef review fill:#fff3cd,stroke:#ffc107",
    "classDef approved fill:#d4edda,stroke:#28a745",
    "classDef wip fill:#e2e3e5,stroke:#6c757d",
    "classDef implemented fill:#cce5ff,stroke:#007bff",
    "classDef deprecated fill:#ffe5e5,stroke:#dc3545",
    "classDef superseded fill:#fff4cc,stroke:#ffb300",
    "classDef removed fill:#efefef,stroke:#6c757d,stroke-dasharray: 5 5",
]


@dataclasses.dataclass
class LinkRef:
    target: str


@dataclasses.dataclass
class SpecInfo:
    links: list[LinkRef]
    status: str
    title: str


def is_record_diagram(value: Any) -> bool:
    return value is not None and isinstance(value, dict) and not isinstance(value, list)


def normalize_string(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    trimmed = value.strip()
    return trimmed if trimmed else None


def parse_call_links(value: Any) -> list[LinkRef]:
    if not isinstance(value, list):
        return []
    links: list[LinkRef] = []
    for item in value:
        scalar_path = normalize_string(item)
        if scalar_path:
            links.append(LinkRef(target=scalar_path))
            continue
        if not is_record_diagram(item):
            continue
        mapped_path = normalize_string(item.get("path"))
        if mapped_path:
            links.append(LinkRef(target=mapped_path))
    return links


def parse_frontmatter_diagram(content: str, file_path: str) -> dict[str, Any]:
    block = extract_frontmatter_from_text(content)
    if block is None:
        return {"links": []}
    data, error = parse_yaml_document(f"{block.yaml}\n")
    if error is not None:
        raise ValueError(f"Invalid YAML frontmatter in {file_path}: {error}")
    if not is_record_diagram(data):
        return {"links": []}
    return {
        "title": normalize_string(data.get("title")),
        "status": normalize_string(data.get("status")),
        "links": parse_call_links(data.get("call")),
    }


def collect_spec_paths(root: str) -> list[str]:
    collected: list[str] = []
    def walk(dir_path: str) -> None:
        try:
            entries = sorted(os.scandir(dir_path), key=lambda e: e.name)
        except OSError:
            return
        for entry in entries:
            full_path = entry.path
            if entry.is_dir():
                walk(full_path)
            elif entry.is_file() and entry.name == "SPEC.md":
                collected.append(full_path)
    walk(root)
    collected.sort()
    return collected


def to_rel(target_path: str, base_path: str) -> str:
    rel = os.path.relpath(target_path, base_path)
    if not rel:
        return ""
    if os.path.isabs(rel) or rel == ".." or rel.startswith(".."):
        return ""
    return rel.replace(os.path.sep, "/")


def make_node_id(spec_path: str) -> str:
    return f"n_{spec_path.encode('utf-8').hex()}"


def escape_mermaid_label(label: str) -> str:
    return label.replace("\\", "\\\\").replace('"', '\\"')


def parse_spec_file(file_path: str) -> SpecInfo:
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()
    fm = parse_frontmatter_diagram(content, file_path)
    spec_dir = os.path.dirname(file_path)
    raw_links: list[LinkRef] = []
    for link in fm["links"]:
        raw = link.target.strip()
        if not raw:
            continue
        raw_path = raw.split("#", 1)[0].strip() if "#" in raw else raw.strip()
        if not raw_path:
            continue
        if os.path.basename(raw_path) != "SPEC.md":
            continue
        raw_links.append(LinkRef(target=os.path.realpath(os.path.join(spec_dir, raw_path))))
    seen: set[str] = set()
    deduped: list[LinkRef] = []
    for link in raw_links:
        edge_key = link.target
        if edge_key in seen:
            continue
        seen.add(edge_key)
        deduped.append(link)
    title = fm["title"] or os.path.basename(spec_dir)
    status = fm["status"] or "draft"
    return SpecInfo(links=deduped, status=status, title=title)


def scan_specs(root: str) -> dict[str, SpecInfo]:
    specs: dict[str, SpecInfo] = {}
    base_parent = os.path.dirname(os.path.realpath(root))
    for spec_path in collect_spec_paths(root):
        rel = to_rel(spec_path, base_parent)
        if rel:
            specs[rel] = parse_spec_file(spec_path)
    return specs


def add_mermaid_nodes(
    lines: list[str],
    specs: dict[str, SpecInfo],
    spec_paths: list[str],
) -> None:
    for spec_path in spec_paths:
        info = specs.get(spec_path)
        if info is None:
            continue
        node_id = make_node_id(spec_path)
        label = escape_mermaid_label(info.title)
        style = STYLE_MAP.get(info.status, "draft")
        lines.append(f'    {node_id}["{label}"]:::{style}')


def add_hierarchy_edges(
    lines: list[str],
    specs: dict[str, SpecInfo],
    spec_paths: list[str],
) -> set[str]:
    edges: set[str] = set()
    for spec_path in spec_paths:
        node_id = make_node_id(spec_path)
        parent_dir = os.path.dirname(os.path.dirname(spec_path))
        if parent_dir == ".":
            continue
        parent_spec_rel = f"{parent_dir}/SPEC.md"
        if parent_spec_rel not in specs:
            continue
        parent_id = make_node_id(parent_spec_rel)
        edge = f"{parent_id}|-->|{node_id}"
        if edge in edges:
            continue
        edges.add(edge)
        lines.append(f"    {parent_id} --> {node_id}")
    return edges


def collect_call_edges(
    specs: dict[str, SpecInfo],
    spec_paths: list[str],
    base_dir: str,
) -> set[str]:
    call_edges: set[str] = set()
    for source_path in spec_paths:
        info = specs.get(source_path)
        if info is None:
            continue
        source_id = make_node_id(source_path)
        for link in info.links:
            target_rel = to_rel(link.target, base_dir)
            if not target_rel or target_rel not in specs:
                continue
            target_id = make_node_id(target_rel)
            call_edges.add(f"{source_id}|{target_id}")
    return call_edges


def sort_call_edges(call_edges: set[str]) -> list[tuple[str, ...]]:
    pairs = [tuple(edge.split("|", 1)) for edge in call_edges]
    return sorted(pairs, key=lambda p: (p[0], p[1]))


def add_simple_edge(
    lines: list[str],
    edges: set[str],
    source_id: str,
    target_id: str,
) -> None:
    edge = f"{source_id}|-->|{target_id}"
    if edge in edges:
        return
    edges.add(edge)
    lines.append(f"    {source_id} --> {target_id}")


def add_call_edges(
    lines: list[str],
    edges: set[str],
    call_edges: set[str],
) -> None:
    rendered_cycle_pairs: set[str] = set()
    for source_id, target_id in sort_call_edges(call_edges):
        if source_id == target_id:
            add_simple_edge(lines, edges, source_id, target_id)
            continue
        reverse_edge = f"{target_id}|{source_id}"
        if reverse_edge not in call_edges:
            add_simple_edge(lines, edges, source_id, target_id)
            continue
        pair = "|".join(sorted([source_id, target_id]))
        if pair in rendered_cycle_pairs:
            continue
        rendered_cycle_pairs.add(pair)
        bidi_edge = f"{source_id}|<-->|{target_id}"
        if bidi_edge in edges:
            continue
        edges.add(bidi_edge)
        lines.append(f"    {source_id} <--> {target_id}")


def add_class_defs(lines: list[str]) -> None:
    lines.append("")
    for class_def in CLASS_DEFS:
        lines.append(f"    {class_def}")


def generate_mermaid(spec_root: str) -> str:
    """Generate a Mermaid flowchart TD diagram from all SPEC.md files under *spec_root*.

    :param spec_root: Absolute path to the spec root directory.
    :return: Complete Mermaid diagram source string.
    """
    specs = scan_specs(spec_root)
    if not specs:
        raise ValueError(f"No SPEC.md files found under {spec_root}")
    base_dir = os.path.dirname(os.path.realpath(spec_root))
    lines: list[str] = ["flowchart TD"]
    sorted_paths = sorted(specs.keys())
    add_mermaid_nodes(lines, specs, sorted_paths)
    edges = add_hierarchy_edges(lines, specs, sorted_paths)
    call_edges = collect_call_edges(specs, sorted_paths, base_dir)
    add_call_edges(lines, edges, call_edges)
    add_class_defs(lines)
    return "\n".join(lines)
