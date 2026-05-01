"""SDD CLI toolkit -- unified command-line interface for spec-driven development.

Provides subcommands for frontmatter extraction, listing, tag collection,
diagram generation, and validation of SPEC, RESEARCH, and CONTRACT documents.
"""

import json
import os
import re
import sys
from pathlib import Path

import argparse

from sdd import (
    extract_frontmatter_from_file,
    extract_frontmatter_from_text,
    find_spec_root,
    generate_mermaid,
    list_by_basename,
    parse_yaml_document,
    resolve_validation_roots,
    validate_changelog_file,
    validate_changelog_layout,
    validate_contract_file,
    validate_research_file,
    validate_spec_file,
)


VALID_KINDS = ("spec", "research", "contract")
VALID_FORMATS = ("json", "jsonl", "yaml", "value", "file")
LIST_KINDS = ("any", "spec", "research", "contract")
DOC_FILE_NAMES: dict[str, str] = {
    "spec": "SPEC.md",
    "research": "RESEARCH.md",
    "contract": "CONTRACT.md",
}
PATH_SEGMENT_SPLIT_RE = r"[\\/]"
URL_SCHEME_RE = r"^[a-zA-Z][a-zA-Z0-9+.\-]*:"


def cli_name() -> str:
    """Return the program name from SDD_CLI_NAME env or default.

    :return: Program name string.
    """
    return os.environ.get("SDD_CLI_NAME", "sdd")


def emit_fail(message: str) -> None:
    """Write an error message to stderr.

    :param message: Error text to emit.
    """
    print(message, file=sys.stderr)


def emit_warn(message: str) -> None:
    """Write a warning message to stderr.

    :param message: Warning text to emit.
    """
    print(f"WARN: {message}", file=sys.stderr)


def resolve_default_spec_path() -> str | None:
    """Resolve the default spec path from ./spec or SDD_SPEC_DIR env var.

    :return: Path string or None if no default found.
    """
    if os.path.isdir("spec"):
        return "spec"
    env_path = os.environ.get("SDD_SPEC_DIR")
    if env_path and os.path.isdir(env_path):
        return env_path
    return None


def script_dir() -> str:
    """Return the scripts directory containing the sdd package.

    :return: Absolute path to the scripts directory (parent of sdd/).
    """
    return str(Path(__file__).resolve().parent.parent)


def skill_root() -> str:
    """Return the resolved skill root directory that owns assets/ and scripts/.

    When ``SDD_SKILL_ROOT`` is set, its value wins. This lets ``sdd.sh`` pass the
    original skill-installed location even when ``__file__`` resolves inside a uvx
    cache that does not ship the sibling assets/ directory. The env override is
    passed through ``Path.resolve()``, which follows symlinks; if the caller mounts
    the skill under a symlink and expects the skill root to remain logical, set
    ``SDD_SKILL_ROOT`` to the already-resolved absolute path.

    :return: Absolute path to the skill root directory.
    """
    env_override = os.environ.get("SDD_SKILL_ROOT")
    if env_override:
        return str(Path(env_override).resolve())
    return str(Path(script_dir()).parent)


def is_record(value: object) -> bool:
    """Check whether a value is a non-null dict-like object.

    :param value: Value to test.
    :return: True if value is a dict (not list, not None).
    """
    return value is not None and isinstance(value, dict) and not isinstance(value, list)


def yaml_to_record(frontmatter_yaml: str) -> dict | None:
    """Parse YAML frontmatter text into a Python dict.

    :param frontmatter_yaml: Raw YAML frontmatter string.
    :return: Parsed dict or None on failure.
    """
    data, error = parse_yaml_document(frontmatter_yaml)
    if error is not None:
        return None
    return data if is_record(data) else None


def normalize_tag(value: object) -> list[str]:
    """Normalize a tag field value into a list of strings.

    :param value: Raw tag value (string, list, or other).
    :return: List of non-empty trimmed tag strings.
    """
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item).strip() for item in value if str(item).strip()]
    scalar = str(value).strip()
    return [scalar] if scalar else []


def json_stringify_ascii(value: object, indent: int | None = None) -> str:
    """Serialize a value to JSON with non-ASCII characters escaped.

    :param value: Object to serialize.
    :param indent: Indentation level (None for compact).
    :return: JSON string with \\uXXXX escapes for non-ASCII.
    """
    raw = json.dumps(value, ensure_ascii=False, indent=indent)
    result: list[str] = []
    for ch in raw:
        code = ord(ch)
        if code > 0x7F:
            result.append(f"\\u{code:04x}")
        else:
            result.append(ch)
    return "".join(result)


def parse_fields(raw_fields: str | None) -> list[str] | None:
    """Parse a comma-separated field list, applying alias mapping.

    :param raw_fields: Comma-separated field string or None.
    :return: List of normalized field names or None.
    """
    if not raw_fields:
        return None
    alias = {"tags": "tag"}
    fields = [
        alias.get(f.strip(), f.strip())
        for f in raw_fields.split(",")
        if f.strip()
    ]
    return fields if fields else None


def cmd_get_frontmatter(args) -> int:
    """Extract and output frontmatter from a single spec/research/contract document.

    Resolves the input path (appending the canonical filename when a directory
    is given), parses its YAML frontmatter, and prints the result in the
    requested format.

    :param args: Parsed argparse namespace for the get-frontmatter subcommand.
    :return: Exit code (0 success, 1 failure).
    """
    kind = args.kind
    raw_path = args.path
    fmt = args.format
    include_yaml = args.include_yaml
    fields = parse_fields(args.fields)
    doc_name = DOC_FILE_NAMES[kind]
    resolved = Path(raw_path).resolve()
    if resolved.is_dir():
        resolved = resolved / doc_name
    doc_path = str(resolved)
    if not os.path.exists(doc_path):
        emit_fail(f"FAIL: Document not found: {doc_path}")
        return 1
    if not os.path.isfile(doc_path):
        emit_fail(f"FAIL: Not a file: {doc_path}")
        return 1
    if Path(doc_path).name != doc_name:
        emit_fail(f"FAIL: kind={kind} requires {doc_name} (got: {Path(doc_path).name})")
        return 1
    with open(doc_path, "r", encoding="utf-8") as f:
        file_text = f.read()
    if fmt == "file":
        print(file_text, end="")
        return 0
    extracted = extract_frontmatter_from_file(doc_path)
    if extracted is None:
        emit_fail(f"FAIL: No YAML frontmatter found: {doc_path}")
        return 1
    fm_yaml = extracted.yaml
    if not fm_yaml.strip():
        emit_fail(f"FAIL: Empty YAML frontmatter: {doc_path}")
        return 1
    data = yaml_to_record(fm_yaml)
    if data is None:
        emit_fail(f"FAIL: Invalid YAML frontmatter: {doc_path}")
        return 1
    if "tag" in data:
        data["tag"] = normalize_tag(data["tag"])
    elif "tags" in data:
        data["tag"] = normalize_tag(data["tags"])
    end_line = extracted.end_line
    if fmt == "yaml":
        print(fm_yaml)
        return 0
    if fmt == "value":
        if not fields or len(fields) != 1:
            emit_fail("FAIL: --format value requires --fields with exactly one field")
            return 1
        field = fields[0]
        if field == "file":
            print(doc_path)
            return 0
        if field == "kind":
            print(kind)
            return 0
        if field == "frontmatter_end_line":
            print(end_line)
            return 0
        val = data.get(field)
        if val is None:
            print("", end="")
        elif isinstance(val, (list, dict)):
            print(json_stringify_ascii(val))
        else:
            print(val)
        return 0
    out: dict = {
        "file": doc_path,
        "kind": kind,
        "frontmatter_end_line": end_line,
    }
    if fields:
        for field in fields:
            if field in ("file", "kind", "frontmatter_end_line"):
                continue
            out[field] = data.get(field)
    else:
        for key, value in data.items():
            if key in ("file", "kind", "frontmatter_end_line"):
                continue
            out[key] = value
    if include_yaml:
        out["frontmatter_yaml"] = fm_yaml
    if fmt == "jsonl":
        print(json_stringify_ascii(out))
    else:
        print(json_stringify_ascii(out, indent=2))
    return 0


def collect_markdown_files(root: str) -> list[str]:
    """Recursively collect all .md files under a root directory.

    :param root: Directory to scan.
    :return: Sorted list of absolute paths.
    """
    files: list[str] = []

    def walk(dir_path: str) -> None:
        try:
            entries = sorted(os.scandir(dir_path), key=lambda e: e.name)
        except OSError:
            return
        dirs: list[str] = []
        md_files: list[str] = []
        for entry in entries:
            full = entry.path
            if entry.is_dir():
                dirs.append(full)
            elif entry.is_file() and entry.name.endswith(".md"):
                md_files.append(full)
        files.extend(sorted(md_files))
        for d in sorted(dirs):
            walk(d)

    walk(root)
    return files


def matches_kind(file_path: str, kind: str) -> bool:
    """Check whether a file matches the given document kind.

    :param file_path: Path to check.
    :param kind: Kind filter (any, spec, research, contract).
    :return: True if the file matches the kind filter.
    """
    if kind == "any":
        return True
    name = os.path.basename(file_path)
    return name == DOC_FILE_NAMES.get(kind, "")


def sanitize_tsv_cell(value: object) -> str:
    """Sanitize a value for TSV table cell output.

    :param value: Value to sanitize.
    :return: String with tabs, CR, and LF replaced by spaces.
    """
    if value is None:
        return ""
    text: str
    if isinstance(value, list):
        text = ",".join(str(v) for v in value)
    elif isinstance(value, dict):
        text = json_stringify_ascii(value)
    else:
        text = str(value)
    return text.replace("\t", " ").replace("\r", " ").replace("\n", " ")


def to_kind_label(file_path: str) -> str:
    """Derive the kind label from a file's basename.

    :param file_path: File path.
    :return: Kind label string (spec, research, contract, or empty).
    """
    base = os.path.basename(file_path)
    for label, fname in DOC_FILE_NAMES.items():
        if base == fname:
            return label
    return ""


def to_subject_str(data: dict) -> tuple[dict | None, str]:
    """Extract subject info as object and versioned string.

    :param data: Frontmatter data dict.
    :return: Tuple of (subject_obj_or_None, subject_version_string).
    """
    subject = data.get("subject")
    if not isinstance(subject, dict):
        return (None, "")
    name = str(subject.get("name", ""))
    version = str(subject.get("version", ""))
    subj_str = f"{name}@{version}" if (name or version) else ""
    return (subject, subj_str)


def to_tag_list(data: dict) -> list[str]:
    """Normalize tags from frontmatter data into a string list.

    :param data: Frontmatter data dict.
    :return: List of tag strings.
    """
    raw = data.get("tag", data.get("tags", []))
    if isinstance(raw, list):
        return [str(v) for v in raw]
    if raw:
        return [str(raw)]
    return []


def build_record_map(
    file_path: str,
    data: dict,
    end_line: int,
    subject_str: str,
    tags: list[str],
) -> dict:
    """Build a flat record map from parsed frontmatter data.

    :param file_path: Absolute file path.
    :param data: Parsed frontmatter dict.
    :param end_line: Frontmatter end line number.
    :param subject_str: Subject version string.
    :param tags: Tag list.
    :return: Dict with standard record fields.
    """
    return {
        "file": file_path,
        "kind": to_kind_label(file_path),
        "title": str(data.get("title", "")),
        "description": str(data.get("description", "")),
        "status": str(data.get("status", "")),
        "last_updated": str(data.get("last_updated", "")),
        "updated": str(data.get("updated", "")),
        "created": str(data.get("created", "")),
        "tag": tags,
        "subject": subject_str,
        "frontmatter_end_line": end_line,
    }


def extract_link_targets(
    data: dict,
    source_file: str,
) -> list[tuple[str, str]]:
    """Extract resolved SPEC.md call-link targets from frontmatter data.

    :param data: Parsed frontmatter dict.
    :param source_file: Source file path for resolving relative links.
    :return: List of (resolved_absolute_path, raw_link_text) tuples.
    """
    raw_calls = data.get("call")
    if not isinstance(raw_calls, list):
        return []
    result: list[tuple[str, str]] = []
    seen: set[str] = set()
    url_re = re.compile(URL_SCHEME_RE)
    for raw_call in raw_calls:
        candidate: str
        if isinstance(raw_call, str):
            candidate = raw_call
        elif isinstance(raw_call, dict):
            candidate = str(raw_call.get("path", ""))
        else:
            continue
        text = candidate.strip()
        if not text:
            continue
        stripped = text.split("#", 1)[0].strip() if "#" in text else text.strip()
        if not stripped:
            continue
        if url_re.match(stripped):
            continue
        if os.path.isabs(stripped):
            continue
        if os.path.basename(stripped) != "SPEC.md":
            continue
        resolved = str(
            Path(os.path.dirname(source_file)).joinpath(stripped).resolve()
        )
        if resolved in seen:
            continue
        seen.add(resolved)
        result.append((resolved, text))
    return result


def resolve_target_paths(
    value: str,
    base_dir: str,
) -> set[str]:
    """Resolve a link target value into candidate absolute paths.

    :param value: Raw target value (may contain fragment).
    :param base_dir: Base directory for relative resolution.
    :return: Set of resolved absolute path candidates.
    """
    stripped = value.split("#", 1)[0].strip() if "#" in value else value.strip()
    if not stripped:
        return set()
    resolved: set[str] = set()
    expanded = (
        os.path.join(os.path.expanduser("~"), stripped[2:])
        if stripped.startswith("~")
        else stripped
    )
    if os.path.isabs(expanded):
        resolved.add(str(Path(expanded).resolve()))
        return resolved
    parts = re.split(PATH_SEGMENT_SPLIT_RE, expanded)
    parts = [p for p in parts if p]
    has_parent = ".." in parts
    spec_root = find_spec_root(base_dir)
    if parts and parts[0] == "spec":
        if spec_root and not has_parent:
            resolved.add(str(Path(os.path.dirname(spec_root)).joinpath(*parts).resolve()))
        else:
            resolved.add(str(Path(base_dir).joinpath(*parts).resolve()))
        return resolved
    resolved.add(str(Path(base_dir).joinpath(*parts).resolve()))
    if spec_root and not has_parent:
        resolved.add(str(Path(spec_root).joinpath(*parts).resolve()))
    return resolved


def matches_filters(
    record: dict,
    frontmatter: dict,
    filters: list[tuple[str, list[str]]],
) -> bool:
    """Test whether a record passes all filter predicates.

    :param record: Flat record map.
    :param frontmatter: Original frontmatter dict.
    :param filters: List of (key, allowed_values) filter pairs.
    :return: True if all filters pass.
    """
    for key, values in filters:
        current = record.get(key) if key in record else frontmatter.get(key)
        if key == "tag":
            tag_values = current if isinstance(current, list) else []
            tag_set = {str(v) for v in tag_values}
            if not any(v in tag_set for v in values):
                return False
            continue
        if isinstance(current, list):
            cur_set = {str(v) for v in current}
            if not any(v in cur_set for v in values):
                return False
            continue
        if str(current) not in values:
            return False
    return True


def load_frontmatter_entry(file_path: str) -> dict | str | None:
    """Load and parse frontmatter from a single file.

    :param file_path: Absolute path to the Markdown file.
    :return: Entry dict on success, error string on failure, None if no frontmatter.
    """
    try:
        extracted = extract_frontmatter_from_file(file_path)
    except Exception as exc:
        return str(exc)
    if extracted is None:
        return None
    yaml_body = extracted.yaml
    end_line = extracted.end_line
    if not yaml_body.strip():
        return "Empty YAML frontmatter"
    data = yaml_to_record(yaml_body)
    if data is None:
        return "Invalid YAML frontmatter"
    tags = to_tag_list(data)
    _, subject_str = to_subject_str(data)
    record = build_record_map(file_path, data, end_line, subject_str, tags)
    return {
        "yamlBody": yaml_body,
        "endLine": end_line,
        "data": data,
        "record": record,
        "subjectStr": subject_str,
        "tags": tags,
    }


def cmd_list_frontmatter(args) -> int:
    """List frontmatter from all matching documents under a spec path.

    Recursively scans for ``.md`` files matching the requested kind, parses
    each file's YAML frontmatter, applies optional filters, and outputs results
    as a TSV table (default) or JSONL records.

    :param args: Parsed argparse namespace for the list-frontmatter subcommand.
    :return: Exit code (0 success, 1 failure).
    """
    spec_path = args.spec_path
    kind = args.kind
    output_jsonl = args.jsonl
    fields = parse_fields(args.fields)
    include_yaml = args.include_yaml
    best_effort = args.best_effort
    inbound_of = args.inbound_of
    raw_filters: list[str] = list(args.filter or []) + [f"tag={v}" for v in (args.tag or [])]
    if include_yaml and not output_jsonl:
        emit_warn("--include-yaml is ignored unless --jsonl is set (continuing in table mode)")
        include_yaml = False
    resolved_spec = str(Path(spec_path).resolve())
    try:
        all_files = collect_markdown_files(resolved_spec)
    except OSError:
        emit_fail(f"FAIL: Path is not a directory: {spec_path}")
        return 1
    files = [f for f in all_files if matches_kind(f, kind)]
    if not files:
        emit_fail(f"FAIL: No markdown files found under {spec_path}")
        return 1
    filters: list[tuple[str, list[str]]] = []
    for rule in raw_filters:
        idx = rule.index("=")
        key = rule[:idx].strip()
        vals = [v.strip() for v in rule[idx + 1:].split(",") if v.strip()]
        if key == "tags":
            key = "tag"
        if not vals:
            emit_fail(f"FAIL: Invalid filter: {rule}")
            return 1
        filters.append((key, vals))
    target_candidates: set[str] | None = None
    if inbound_of:
        target_candidates = resolve_target_paths(inbound_of, resolved_spec)
    output_lines: list[str] = []
    if not output_jsonl:
        if inbound_of:
            output_lines.append("target\tsource\traw_link")
        elif fields:
            cols = ["file"] + [f for f in fields if f != "file"]
            output_lines.append("\t".join(cols))
        else:
            output_lines.append(
                "file\ttitle\tstatus\tlast_updated\tupdated\tcreated\ttag\tsubject"
            )
    parse_failures = 0
    for file_path in files:
        entry = load_frontmatter_entry(file_path)
        if isinstance(entry, str):
            parse_failures += 1
            emit_fail(f"FAIL [{file_path}]: {entry}")
            if not best_effort:
                return 1
            continue
        if entry is None:
            continue
        rec = entry["record"]
        data = entry["data"]
        if not matches_filters(rec, data, filters):
            continue
        if inbound_of:
            targets = extract_link_targets(data, file_path)
            for tpath, raw_link in targets:
                if target_candidates and tpath not in target_candidates:
                    continue
                row: dict = {
                    "target": inbound_of,
                    "source": file_path,
                    "raw_link": raw_link,
                    "frontmatter_end_line": entry["endLine"],
                }
                if include_yaml:
                    row["frontmatter_yaml"] = entry["yamlBody"]
                if output_jsonl:
                    output_lines.append(json_stringify_ascii(row))
                else:
                    output_lines.append(
                        "\t".join(sanitize_tsv_cell(v) for v in [inbound_of, file_path, raw_link])
                    )
            continue
        if output_jsonl:
            out: dict
            if fields:
                out = {"file": file_path}
                for fld in fields:
                    if fld == "file":
                        continue
                    if fld == "subject":
                        out["subject"] = data.get("subject", "") or ""
                        continue
                    if fld == "subject_str":
                        out["subject_str"] = entry["subjectStr"]
                        continue
                    out[fld] = rec.get(fld, data.get(fld, ""))
            else:
                out = dict(data)
                out["file"] = file_path
                out["frontmatter_end_line"] = entry["endLine"]
            if include_yaml:
                out["frontmatter_yaml"] = entry["yamlBody"]
            output_lines.append(json_stringify_ascii(out))
        elif fields:
            cells: list = [rec["file"]]
            for fld in fields:
                if fld == "file":
                    continue
                cells.append(rec.get(fld, data.get(fld, "")))
            output_lines.append("\t".join(sanitize_tsv_cell(c) for c in cells))
        else:
            cells = [
                rec["file"],
                rec["title"],
                rec["status"],
                rec["last_updated"],
                rec["updated"],
                rec["created"],
                ",".join(rec["tag"]),
                rec["subject"],
            ]
            output_lines.append("\t".join(sanitize_tsv_cell(c) for c in cells))
    for line in output_lines:
        print(line)
    if best_effort and parse_failures > 0:
        emit_warn(
            f"Skipped {parse_failures} file(s) due to invalid, empty, or unterminated "
            "YAML frontmatter (--best-effort)"
        )
    return 0


def cmd_list_tags(args) -> int:
    """Collect and output tags from all matching document frontmatter entries.

    Scans for ``.md`` files under *spec_path* matching the requested kind,
    extracts ``tag`` / ``tags`` values from each frontmatter, aggregates counts,
    and prints sorted tags (optionally with occurrence counts).

    :param args: Parsed argparse namespace for the list-tags subcommand.
    :return: Exit code (0 success, 1 failure).
    """
    kind = args.kind
    count = args.count
    spec_path = args.spec_path
    resolved = str(Path(spec_path).resolve())
    try:
        all_files = collect_markdown_files(resolved)
    except OSError:
        emit_fail(f"FAIL: Path is not a directory: {spec_path}")
        return 1
    if kind != "any":
        all_files = [f for f in all_files if matches_kind(f, kind)]
    if not all_files:
        emit_fail(f"FAIL: No markdown files found under {spec_path}")
        return 1
    counter: dict[str, int] = {}
    for file_path in all_files:
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                text = f.read()
        except OSError as exc:
            emit_fail(f"FAIL [{file_path}]: {exc}")
            return 1
        block = extract_frontmatter_from_text(text)
        if block is None:
            continue
        if not block.yaml.strip():
            emit_fail(f"FAIL [{file_path}]: Empty YAML frontmatter")
            return 1
        data = yaml_to_record(block.yaml)
        if data is None:
            emit_fail(f"FAIL [{file_path}]: Invalid YAML frontmatter")
            return 1
        raw_tags = data.get("tag", data.get("tags", []))
        tags: list[str]
        if isinstance(raw_tags, list):
            tags = [str(t).strip() for t in raw_tags if str(t).strip()]
        else:
            scalar = str(raw_tags).strip()
            tags = [scalar] if scalar else []
        for tag in tags:
            counter[tag] = counter.get(tag, 0) + 1
    for tag in sorted(counter):
        if count:
            print(f"{tag}\t{counter[tag]}")
        else:
            print(tag)
    return 0


def cmd_generate_diagram(args) -> int:
    """Generate and print a Mermaid flowchart diagram from SPEC.md files.

    Walks *spec_root* recursively for all ``SPEC.md`` files, parses their
    frontmatter for title/status/call links, and renders a Mermaid ``flowchart TD``
    to stdout.

    :param args: Parsed argparse namespace for the generate-diagram subcommand.
    :return: Exit code (0 success, 1 failure).
    """
    spec_root_arg = args.spec_root
    root = str(Path(spec_root_arg).resolve())
    if not os.path.isdir(root):
        emit_fail(f"FAIL: Directory not found: {spec_root_arg}")
        return 1
    try:
        diagram = generate_mermaid(root)
    except ValueError as exc:
        emit_fail(str(exc))
        return 1
    print(diagram)
    return 0


def cmd_validate(args) -> int:
    """Validate all spec documents under a path against schemas and lint rules.

    Scans for ``SPEC.md``, ``RESEARCH.md``, ``CONTRACT.md``, and ``CHANGELOG.md``
    files under *spec_path*, runs JSON Schema validation on frontmatter content,
    checks link integrity, date formats, scaffolding markers, changelog layout,
    and other domain-specific rules. Prints a summary and exits non-zero on any
    error.

    :param args: Parsed argparse namespace for the validate subcommand.
    :return: Exit code (0 all pass, 1 any failure).
    """
    import jsonschema

    spec_path_arg = args.spec_path
    spec_path = str(Path(spec_path_arg).resolve())
    if not os.path.isdir(spec_path):
        emit_fail(f"FAIL: Path is not a directory: {spec_path_arg}")
        return 1
    roots = resolve_validation_roots(spec_path)
    if roots is None:
        emit_fail(
            f"FAIL: Path must be under spec/ or contain a spec/ directory: {spec_path_arg}"
        )
        return 1
    spec_root, scan_root = roots
    schema_dir = os.path.join(skill_root(), "assets", "schemas")
    spec_schema = os.path.join(schema_dir, "spec-frontmatter.schema.json")
    research_schema = os.path.join(schema_dir, "research-frontmatter.schema.json")
    contract_schema = os.path.join(schema_dir, "contract-frontmatter.schema.json")
    for schema_path in (spec_schema, research_schema, contract_schema):
        if not os.path.exists(schema_path):
            emit_fail(f"FAIL: Schema not found: {schema_path}")
            return 1
    ajv = jsonschema.Draft7Validator
    with open(spec_schema, "r", encoding="utf-8") as f:
        spec_validator = ajv(json.load(f))
    with open(research_schema, "r", encoding="utf-8") as f:
        research_validator = ajv(json.load(f))
    with open(contract_schema, "r", encoding="utf-8") as f:
        contract_validator = ajv(json.load(f))
    spec_files = list_by_basename(scan_root, "SPEC.md")
    research_files = list_by_basename(scan_root, "RESEARCH.md")
    contract_files = list_by_basename(scan_root, "CONTRACT.md")
    if not spec_files and not research_files and not contract_files:
        emit_fail(
            f"FAIL: No SPEC.md, RESEARCH.md, or CONTRACT.md files found under {scan_root}"
        )
        return 1
    total = 0
    passed = 0
    failed = 0
    changelog_layout_failed = 0
    has_error = False

    def emit_failure(msg: str | None) -> None:
        nonlocal has_error
        if msg:
            emit_fail(msg)
        has_error = True

    def apply_result(result) -> None:
        nonlocal total, passed, failed
        total += 1
        for err in result.errors:
            emit_failure(err)
        if result.passed:
            passed += 1
        else:
            failed += 1

    root_clangelog_file, layout_errors = validate_changelog_layout(spec_root)
    for err in layout_errors:
        changelog_layout_failed += 1
        total += 1
        failed += 1
        emit_failure(err)
    for fp in spec_files:
        apply_result(validate_spec_file(fp, spec_schema, spec_validator, spec_root))
    for fp in research_files:
        apply_result(validate_research_file(fp, research_schema, research_validator))
    for fp in contract_files:
        apply_result(validate_contract_file(fp, contract_schema, contract_validator))
    if root_clangelog_file is not None:
        apply_result(validate_changelog_file(root_clangelog_file))
    print("Validation Summary")
    print(f"- Total checks: {total}")
    print(f"- Passed: {passed}")
    print(f"- Failed: {failed}")
    print(f"- Changelog layout failures: {changelog_layout_failed}")
    if has_error:
        return 1
    print(f"OK: Validation complete for {scan_root}")
    return 0


def build_parser() -> "argparse.ArgumentParser":
    """Construct the top-level argparse parser with all subcommands.

    :return: Configured ArgumentParser instance.
    """
    prog = cli_name()
    parser = argparse.ArgumentParser(
        prog=prog,
        description="SDD CLI toolkit for spec-driven development.",
    )
    subs = parser.add_subparsers(dest="command", help="Available subcommands")
    gf = subs.add_parser(
        "get-frontmatter",
        help="Extract frontmatter from a single document",
    )
    gf.add_argument("kind", choices=VALID_KINDS, help="Document kind")
    gf.add_argument("path", help="File or directory path")
    gf.add_argument(
        "--format",
        choices=VALID_FORMATS,
        default="json",
        help="Output format (default: json)",
    )
    gf.add_argument("--fields", help="Comma-separated field names to include")
    gf.add_argument(
        "--include-yaml",
        action="store_true",
        default=False,
        help="Include raw frontmatter YAML in output",
    )
    gf.set_defaults(func=cmd_get_frontmatter)
    lf = subs.add_parser(
        "list-frontmatter",
        help="List frontmatter from all matching documents",
    )
    lf.add_argument(
        "spec_path",
        nargs="?",
        help="Spec root directory (default: ./spec or $SDD_SPEC_DIR)",
    )
    lf.add_argument(
        "--kind",
        choices=LIST_KINDS,
        default="any",
        help="Filter by document kind (default: any)",
    )
    lf.add_argument("--jsonl", action="store_true", default=False, help="Output JSONL")
    lf.add_argument("--fields", help="Comma-separated field names")
    lf.add_argument(
        "--include-yaml",
        action="store_true",
        default=False,
        help="Include raw frontmatter YAML (requires --jsonl)",
    )
    lf.add_argument(
        "--filter",
        action="append",
        default=[],
        metavar="KEY=V1,V2",
        help="Filter by frontmatter field values (repeatable)",
    )
    lf.add_argument(
        "--tag",
        action="append",
        default=[],
        metavar="VALUE",
        help="Filter by tag value (repeatable; shorthand for --filter tag=VALUE)",
    )
    lf.add_argument(
        "--inbound-of",
        metavar="SPEC_PATH",
        help="Show inbound call links targeting this spec",
    )
    lf.add_argument(
        "--best-effort",
        action="store_true",
        default=False,
        help="Continue past files with invalid frontmatter",
    )
    lf.set_defaults(func=cmd_list_frontmatter)
    lt = subs.add_parser("list-tags", help="Collect and list tags from frontmatter")
    lt.add_argument(
        "spec_path",
        nargs="?",
        help="Spec root directory (default: ./spec or $SDD_SPEC_DIR)",
    )
    lt.add_argument(
        "--kind",
choices=LIST_KINDS,
        default="any",
        help="Filter by document kind (default: any)",
    )
    lt.add_argument(
        "--count",
        action="store_true",
        default=False,
        help="Show occurrence counts alongside tags",
    )
    lt.set_defaults(func=cmd_list_tags)
    gd = subs.add_parser(
        "generate-diagram",
        help="Generate Mermaid dependency diagram from SPEC.md files",
    )
    gd.add_argument(
        "spec_root",
        nargs="?",
        help="Root directory containing SPEC.md files (default: ./spec or $SDD_SPEC_DIR)",
    )
    gd.set_defaults(func=cmd_generate_diagram)
    vl = subs.add_parser("validate", help="Validate all spec documents")
    vl.add_argument(
        "spec_path",
        nargs="?",
        help="Path to validate (default: ./spec or $SDD_SPEC_DIR)",
    )
    vl.set_defaults(func=cmd_validate)
    return parser


def main() -> int:
    """Parse command-line arguments and dispatch to the appropriate subcommand handler.

    Reads ``sys.argv``, constructs the argument parser, resolves default spec
    paths when positional arguments are omitted, invokes the handler for the
    selected subcommand, and returns the resulting exit code.

    The environment variable ``SDD_CLI_NAME`` overrides the program name shown
    in help text and usage messages.

    :return: Exit code (0 on success, 1 on failure).
    """
    parser = build_parser()
    args = parser.parse_args()
    if not hasattr(args, "func"):
        parser.print_help(sys.stderr)
        return 1
    func = args.func
    resolve_attrs = {"spec_path"}
    for attr in resolve_attrs:
        if hasattr(args, attr) and getattr(args, attr) is None:
            default = resolve_default_spec_path()
            if default is None:
                emit_fail(
                    f"FAIL: {attr} is required (no ./spec directory or $SDD_SPEC_DIR set)"
                )
                return 1
            setattr(args, attr, default)
    return func(args)


if __name__ == "__main__":
    sys.exit(main())
