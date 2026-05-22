#!/usr/bin/env -S uv run
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""Validate repository harness installation and structure."""

from __future__ import annotations

import json
import os
import re
import stat
import sys
from pathlib import Path
from typing import Iterable, NamedTuple

ROOT = Path.cwd()
STACK = "uv"
MANIFEST_PATH = "docs/harness/manifest.json"


class Finding(NamedTuple):
    """Represents a validation finding with severity, category, and message."""

    severity: str
    category: str
    message: str


class LeakPattern(NamedTuple):
    """Represents a compiled pattern to detect in active assets."""

    pattern: re.Pattern[str]
    label: str


def read_text(path: Path) -> str:
    if path.is_symlink():
        target = allowed_root_contract_target(path)
        if target is None:
            return ""
        path = target
    try:
        return path.read_text(encoding="utf-8")
    except OSError:
        return ""


def is_executable(path: Path) -> bool:
    if path.is_symlink():
        target = allowed_root_contract_target(path)
        if target is None:
            return False
        path = target
    try:
        return bool(path.stat().st_mode & stat.S_IXUSR)
    except OSError:
        return False


def first_line(path: Path) -> str:
    lines = read_text(path).splitlines()
    return lines[0] if lines else ""


def relative(path: Path) -> str:
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


def allowed_root_contract_target(path: Path) -> Path | None:
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
    return (
        target
        if target.parent == ROOT and not target.is_symlink() and target.is_file()
        else None
    )


def is_safe_file(path: Path) -> bool:
    if path.is_symlink():
        return allowed_root_contract_target(path) is not None
    return path.is_file()


def is_safe_directory(path: Path) -> bool:
    if path.is_symlink():
        return False
    return path.is_dir()


def safe_walk(base: Path) -> tuple[Path, ...]:
    if base.is_symlink() or base.is_file():
        return ()
    if not base.is_dir():
        return ()
    output: list[Path] = []
    for current, directories, files in os.walk(base, followlinks=False):
        current_path = Path(current)
        kept_directories = []
        for name in directories:
            child = current_path / name
            if not child.is_symlink():
                kept_directories.append(name)
        directories[:] = kept_directories
        for name in files:
            child = current_path / name
            if not child.is_symlink():
                output.append(child)
    return tuple(output)


def safe_file_or_walk(base: Path) -> tuple[Path, ...]:
    if base.is_symlink() and allowed_root_contract_target(base) is None:
        return ()
    if is_safe_file(base):
        return (base,)
    return safe_walk(base)


def load_manifest() -> dict[str, object]:
    path = ROOT / MANIFEST_PATH
    if path.is_symlink():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except OSError:
        return {}
    except json.JSONDecodeError:
        return {}


def manifest_items(manifest: dict[str, object], key: str) -> tuple[str, ...]:
    section = manifest.get(key)
    if isinstance(section, dict):
        items = section.get("items", [])
        if isinstance(items, list):
            return tuple(item for item in items if isinstance(item, str))
    if isinstance(section, list):
        return tuple(item for item in section if isinstance(item, str))
    return ()


def manifest_object_items(
    manifest: dict[str, object], key: str
) -> tuple[dict[str, object], ...]:
    section = manifest.get(key)
    if isinstance(section, dict):
        items = section.get("items", [])
        if isinstance(items, list):
            return tuple(item for item in items if isinstance(item, dict))
    if isinstance(section, list):
        return tuple(item for item in section if isinstance(item, dict))
    return ()


def build_leak_patterns(manifest: dict[str, object]) -> tuple[LeakPattern, ...]:
    section = manifest.get("leakPatterns")
    if not isinstance(section, dict):
        return ()
    patterns_data = section.get("items", [])
    if not isinstance(patterns_data, list):
        return ()
    output: list[LeakPattern] = []
    for item in patterns_data:
        if not isinstance(item, dict):
            continue
        pattern_str = item.get("pattern")
        label = item.get("label")
        if isinstance(pattern_str, str) and isinstance(label, str):
            try:
                compiled = re.compile(pattern_str)
                output.append(LeakPattern(compiled, label))
            except re.error:
                pass
    return tuple(output)


def severity_for(manifest: dict[str, object], category: str) -> str:
    section = manifest.get(category)
    if isinstance(section, dict):
        value = section.get("severity")
        if value in ("ERROR", "WARN", "INFO"):
            return value
    return "ERROR"


def validate_manifest_exists(manifest: dict[str, object]) -> tuple[Finding, ...]:
    findings: list[Finding] = []
    path = ROOT / MANIFEST_PATH
    if path.is_symlink():
        findings.append(
            Finding(
                severity_for(manifest, "manifestParity"),
                "manifestParity",
                "manifest file is a symlink; must be a regular file",
            )
        )
        return tuple(findings)
    if not is_safe_file(path):
        findings.append(
            Finding(
                "ERROR",
                "manifestParity",
                f"manifest file missing: {MANIFEST_PATH}",
            )
        )
        return tuple(findings)
    if not load_manifest():
        findings.append(
            Finding(
                "ERROR",
                "manifestParity",
                f"manifest file invalid or empty JSON: {MANIFEST_PATH}",
            )
        )
        return tuple(findings)
    return tuple(findings)


def validate_structure(manifest: dict[str, object]) -> tuple[Finding, ...]:
    findings: list[Finding] = []
    required_files = manifest_items(manifest, "requiredFiles")
    required_dirs = manifest_items(manifest, "requiredDirectories")
    keep_files = manifest_items(manifest, "emptyDirectoryKeepFiles")
    for path in required_files:
        if not is_safe_file(ROOT / path):
            findings.append(
                Finding(
                    severity_for(manifest, "requiredFiles"),
                    "requiredFiles",
                    f"missing file: {path}",
                )
            )
    for path in required_dirs:
        if not is_safe_directory(ROOT / path):
            findings.append(
                Finding(
                    severity_for(manifest, "requiredDirectories"),
                    "requiredDirectories",
                    f"missing directory: {path}",
                )
            )
    for keep in keep_files:
        keep_path = ROOT / keep
        directory = keep_path.parent
        if not is_safe_directory(directory):
            continue
        real_files = [p for p in directory.iterdir() if p.name != ".gitkeep"]
        if not real_files and not is_safe_file(keep_path):
            findings.append(
                Finding(
                    severity_for(manifest, "emptyDirectoryKeepFiles"),
                    "emptyDirectoryKeepFiles",
                    f"empty directory must keep placeholder or real files: {relative(directory)}",
                )
            )
    return tuple(findings)


def validate_docs_headings(manifest: dict[str, object]) -> tuple[Finding, ...]:
    findings: list[Finding] = []
    required_docs = manifest_items(manifest, "requiredFiles")
    required_headings = manifest_items(manifest, "requiredDocHeadings")
    for doc in required_docs:
        if not doc.startswith("docs/") or not doc.endswith(".md"):
            continue
        path = ROOT / doc
        if not is_safe_file(path):
            continue
        text = read_text(path)
        for heading in required_headings:
            if heading not in text:
                findings.append(
                    Finding(
                        severity_for(manifest, "requiredDocHeadings"),
                        "requiredDocHeadings",
                        f"doc missing {heading}: {doc}",
                    )
                )
    return tuple(findings)


def validate_content_checks(manifest: dict[str, object]) -> tuple[Finding, ...]:
    findings: list[Finding] = []
    checks_data = manifest_object_items(manifest, "requiredContentChecks")
    if not checks_data:
        return ()
    for check in checks_data:
        if not isinstance(check, dict):
            continue
        files_list = check.get("files")
        contains_all = check.get("containsAll")
        failure_msg = check.get("failureMessage")
        if (
            not isinstance(files_list, list)
            or not isinstance(contains_all, list)
            or not isinstance(failure_msg, str)
        ):
            continue
        combined_text_parts: list[str] = []
        for file_name in files_list:
            if not isinstance(file_name, str):
                continue
            path = ROOT / file_name
            if is_safe_file(path):
                combined_text_parts.append(read_text(path))
        combined_text = "\n".join(combined_text_parts)
        missing_substr = False
        for substr in contains_all:
            if not isinstance(substr, str):
                continue
            if substr not in combined_text:
                missing_substr = True
                break
        if missing_substr:
            findings.append(
                Finding(
                    severity_for(manifest, "requiredContentChecks"),
                    "requiredContentChecks",
                    failure_msg,
                )
            )
    return tuple(findings)


def validate_agents(manifest: dict[str, object]) -> tuple[Finding, ...]:
    findings: list[Finding] = []
    directory = ROOT / ".claude/agents"
    files = tuple(
        sorted(
            path
            for path in safe_walk(directory)
            if path.parent == directory and path.suffix == ".md"
        )
    )
    if not files:
        findings.append(
            Finding(
                severity_for(manifest, "agentFrontmatter"),
                "agentFrontmatter",
                ".claude/agents must contain at least one .md agent",
            )
        )
    for path in files:
        text = read_text(path)
        if not text.startswith("---"):
            findings.append(
                Finding(
                    severity_for(manifest, "agentFrontmatter"),
                    "agentFrontmatter",
                    f"agent missing frontmatter: {relative(path)}",
                )
            )
        if not re.search(r"(?m)^name:\s*[-a-z0-9]+\s*$", text):
            findings.append(
                Finding(
                    severity_for(manifest, "agentFrontmatter"),
                    "agentFrontmatter",
                    f"agent missing name: {relative(path)}",
                )
            )
        if not re.search(r"(?m)^description:\s*.+$", text):
            findings.append(
                Finding(
                    severity_for(manifest, "agentFrontmatter"),
                    "agentFrontmatter",
                    f"agent missing description: {relative(path)}",
                )
            )
    return tuple(findings)


def validate_skills(manifest: dict[str, object]) -> tuple[Finding, ...]:
    findings: list[Finding] = []
    directory = ROOT / ".claude/skills"
    files = tuple(
        sorted(path for path in safe_walk(directory) if path.name == "SKILL.md")
    )
    if not files:
        findings.append(
            Finding(
                severity_for(manifest, "skillFrontmatter"),
                "skillFrontmatter",
                ".claude/skills must contain at least one SKILL.md",
            )
        )
    for path in files:
        text = read_text(path)
        if not text.startswith("---"):
            findings.append(
                Finding(
                    severity_for(manifest, "skillFrontmatter"),
                    "skillFrontmatter",
                    f"skill missing frontmatter: {relative(path)}",
                )
            )
        if not re.search(r"(?m)^description:\s*.+$", text):
            findings.append(
                Finding(
                    severity_for(manifest, "skillFrontmatter"),
                    "skillFrontmatter",
                    f"skill missing description: {relative(path)}",
                )
            )
    return tuple(findings)


def validate_templates(manifest: dict[str, object]) -> tuple[Finding, ...]:
    findings: list[Finding] = []
    template_groups = manifest_items(manifest, "templateGroups")
    for group in template_groups:
        if not is_safe_directory(ROOT / f"docs/harness/templates/{group}"):
            findings.append(
                Finding(
                    severity_for(manifest, "templateGroups"),
                    "templateGroups",
                    f"missing template group: docs/harness/templates/{group}",
                )
            )
    return tuple(findings)


def validate_active_assets(manifest: dict[str, object]) -> tuple[Finding, ...]:
    findings: list[Finding] = []
    assets = manifest.get("activeAssets")
    if not isinstance(assets, dict):
        return ()
    bases_data = assets.get("bases", [])
    excluded_data = assets.get("excludedSubtrees", [])
    exts_data = assets.get("extensions", [])
    if not isinstance(bases_data, list):
        return ()
    active_roots = tuple(ROOT / item for item in bases_data if isinstance(item, str))
    excluded_paths = tuple(
        ROOT / item for item in (excluded_data if isinstance(excluded_data, list) else ())
        if isinstance(item, str)
    )
    extensions = (
        set(
            f".{ext}" for ext in exts_data
            if isinstance(exts_data, list) and isinstance(ext, str)
        )
        if isinstance(exts_data, list)
        else {".md", ".txt", ".json", ".yml", ".yaml"}
    )
    leak_patterns = build_leak_patterns(manifest)
    for base in active_roots:
        paths = safe_file_or_walk(base)
        for path in paths:
            if not path.is_file() or path.suffix not in extensions:
                continue
            if any(path == excluded or excluded in path.parents for excluded in excluded_paths):
                continue
            text = read_text(path)
            for leak in leak_patterns:
                if leak.pattern.search(text):
                    findings.append(
                        Finding(
                            severity_for(manifest, "leakPatterns"),
                            "leakPatterns",
                            f"{leak.label} in active asset: {relative(path)}",
                        )
                    )
    return tuple(findings)


def hook_command(pre_push_text: str) -> str:
    for line in pre_push_text.splitlines():
        if line.startswith("# Harness validation command: "):
            return line.removeprefix("# Harness validation command: ").strip()
    return ""


def validate_one_hook(
    name: str, stage: str, manifest: dict[str, object]
) -> tuple[str, tuple[Finding, ...]]:
    hook = ROOT / f"docs/harness/git-hooks/{name}"
    hook_text = ""
    findings: list[Finding] = []
    if is_safe_file(hook):
        hook_text = read_text(hook)
        if first_line(hook) != "#!/usr/bin/env sh":
            findings.append(
                Finding(
                    severity_for(manifest, "hookFirstLine"),
                    "hookFirstLine",
                    f"{name} hook must use #!/usr/bin/env sh",
                )
            )
        if not is_executable(hook):
            findings.append(
                Finding(
                    severity_for(manifest, "hookExecutable"),
                    "hookExecutable",
                    f"{name} hook must be executable: {relative(hook)}",
                )
            )
        if f"Harness generated hook: {name}" not in hook_text:
            findings.append(
                Finding(
                    severity_for(manifest, "hookGeneratedMarker"),
                    "hookGeneratedMarker",
                    f"{name} hook must contain generated marker",
                )
            )
        if f"Harness stage: {stage}" not in hook_text:
            findings.append(
                Finding(
                    severity_for(manifest, "hookStage"),
                    "hookStage",
                    f"{name} hook must contain {stage} stage marker",
                )
            )
        if "packaged placeholder is replaced during harness installation" in hook_text:
            findings.append(
                Finding(
                    severity_for(manifest, "hookGeneratedMarker"),
                    "hookGeneratedMarker",
                    f"{name} hook must be installer-generated selected-mode content",
                )
            )
    return hook_text, tuple(findings)


def validate_hooks(manifest: dict[str, object]) -> tuple[Finding, ...]:
    findings: list[Finding] = []
    hook_stages_wrap = manifest.get("hookStages")
    expected_commands_wrap = manifest.get("expectedValidationCommands")
    if not isinstance(hook_stages_wrap, dict) or not isinstance(
        expected_commands_wrap, dict
    ):
        findings.append(
            Finding(
                "ERROR",
                "hookValidationCommand",
                "hook stages or validation commands missing from manifest",
            )
        )
        return tuple(findings)
    hook_stages_data = hook_stages_wrap
    if "severity" in hook_stages_data:
        hook_stages_data = {k: v for k, v in hook_stages_data.items() if k != "severity"}
    expected_commands_data = expected_commands_wrap
    if "severity" in expected_commands_data:
        expected_commands_data = {k: v for k, v in expected_commands_data.items() if k != "severity"}
    stack_stages = hook_stages_data.get(STACK)
    if not isinstance(stack_stages, dict):
        findings.append(
            Finding(
                "ERROR",
                "hookValidationCommand",
                f"hook stages for stack '{STACK}' missing from manifest",
            )
        )
        return tuple(findings)
    expected_command_obj = expected_commands_data.get(STACK)
    if not isinstance(expected_command_obj, str):
        findings.append(
            Finding(
                "ERROR",
                "hookValidationCommand",
                f"validation command for stack '{STACK}' missing from manifest",
            )
        )
        return tuple(findings)
    pre_commit_stage = stack_stages.get("preCommit")
    pre_push_stage = stack_stages.get("prePush")
    if not isinstance(pre_commit_stage, str) or not isinstance(pre_push_stage, str):
        findings.append(
            Finding(
                "ERROR",
                "hookValidationCommand",
                f"hook stage values for '{STACK}' must be strings",
            )
        )
        return tuple(findings)
    pre_commit_text, pre_commit_findings = validate_one_hook(
        "pre-commit", pre_commit_stage, manifest
    )
    findings.extend(pre_commit_findings)
    pre_push_text, pre_push_findings = validate_one_hook(
        "pre-push", pre_push_stage, manifest
    )
    findings.extend(pre_push_findings)
    if re.search(
        r"(^|\s)(uv|bun|gradle|mvn)(\s|$)|\./gradlew|harnessValidate|"
        r"harness_validate\.py|harness-validate\.ts",
        pre_commit_text,
    ):
        findings.append(
            Finding(
                severity_for(manifest, "hookValidationCommand"),
                "hookValidationCommand",
                "pre-commit hook must not run full stack validation commands",
            )
        )
    command = hook_command(pre_push_text)
    if not command:
        findings.append(
            Finding(
                severity_for(manifest, "hookValidationCommand"),
                "hookValidationCommand",
                "pre-push hook must declare Harness validation command",
            )
        )
        return tuple(findings)
    if command != expected_command_obj:
        findings.append(
            Finding(
                severity_for(manifest, "hookValidationCommand"),
                "hookValidationCommand",
                f"pre-push hook declares unsupported validation command: {command}",
            )
        )
        return tuple(findings)
    if command not in pre_push_text.splitlines():
        findings.append(
            Finding(
                severity_for(manifest, "hookValidationCommand"),
                "hookValidationCommand",
                "pre-push hook must run the declared validation command",
            )
        )
    for ci_file in [".github/workflows/harness.yml", ".gitlab-ci.yml"]:
        path = ROOT / ci_file
        if (
            path.exists()
            and is_safe_file(path)
            and command not in read_text(path)
        ):
            findings.append(
                Finding(
                    severity_for(manifest, "ciCommandMatch"),
                    "ciCommandMatch",
                    f"{ci_file}: CI command mismatch - expected {command}",
                )
            )
    return tuple(findings)


def validate_env_shebangs(manifest: dict[str, object]) -> tuple[Finding, ...]:
    findings: list[Finding] = []
    shebang_bases = manifest_items(manifest, "envShebangBases")
    if not shebang_bases:
        return ()
    bases = tuple(ROOT / item for item in shebang_bases if isinstance(item, str))
    for base in bases:
        if not base.is_dir():
            continue
        for path in safe_walk(base):
            if not path.is_file() or not is_executable(path):
                continue
            line = first_line(path)
            if line.startswith("#!") and not line.startswith("#!/usr/bin/env "):
                findings.append(
                    Finding(
                        severity_for(manifest, "envShebang"),
                        "envShebang",
                        f"executable script should use /usr/bin/env shebang: {relative(path)}",
                    )
                )
    return tuple(findings)


def validate_completed_plans(manifest: dict[str, object]) -> tuple[Finding, ...]:
    findings: list[Finding] = []
    completed_wrap = manifest.get("completedPlanDirectory")
    unfinished_wrap = manifest.get("unfinishedTaskPattern")
    if not isinstance(completed_wrap, dict) or not isinstance(unfinished_wrap, dict):
        return ()
    completed_dir_name = completed_wrap.get("value")
    unfinished_pattern_str = unfinished_wrap.get("value")
    if not isinstance(completed_dir_name, str) or not isinstance(
        unfinished_pattern_str, str
    ):
        return ()
    completed_dir = ROOT / completed_dir_name
    if not is_safe_directory(completed_dir):
        return ()
    try:
        unfinished_pattern = re.compile(unfinished_pattern_str)
    except re.error:
        findings.append(
            Finding(
                "ERROR",
                "completedPlanUnfinishedTask",
                f"invalid unfinishedTaskPattern regex: {unfinished_pattern_str}",
            )
        )
        return tuple(findings)
    files = tuple(
        sorted(
            path
            for path in completed_dir.iterdir()
            if path.is_file() and path.suffix == ".md" and path.name != ".gitkeep"
        )
    )
    for path in files:
        text = read_text(path)
        if unfinished_pattern.search(text):
            findings.append(
                Finding(
                    severity_for(manifest, "completedPlanUnfinishedTask"),
                    "completedPlanUnfinishedTask",
                    f"completed plan has unchecked tasks: {relative(path)}",
                )
            )
    return tuple(findings)


def validate(manifest: dict[str, object]) -> tuple[Finding, ...]:
    all_findings: list[tuple[Finding, ...]] = [
        validate_manifest_exists(manifest),
        validate_structure(manifest),
        validate_docs_headings(manifest),
        validate_content_checks(manifest),
        validate_agents(manifest),
        validate_skills(manifest),
        validate_templates(manifest),
        validate_active_assets(manifest),
        validate_hooks(manifest),
        validate_env_shebangs(manifest),
        validate_completed_plans(manifest),
    ]
    combined = tuple(f for findings in all_findings for f in findings)
    deduped = tuple(
        dict.fromkeys(
            (f.severity, f.category, f.message) for f in combined
        ).keys()
    )
    return tuple(Finding(sev, cat, msg) for sev, cat, msg in deduped)


def main() -> int:
    manifest_exists_failures = validate_manifest_exists({})
    if manifest_exists_failures:
        print("Harness validation failed", file=sys.stderr)  # noqa: T201
        for finding in manifest_exists_failures:
            print(f"[{finding.severity}] {finding.message}", file=sys.stderr)  # noqa: T201
        return 1
    manifest = load_manifest()
    if not manifest:
        print("Harness validation failed", file=sys.stderr)  # noqa: T201
        print("[ERROR] manifest is empty", file=sys.stderr)  # noqa: T201
        return 1
    findings = validate(manifest)
    grouped: dict[str, list[Finding]] = {}
    severity_order = ["ERROR", "WARN", "INFO"]
    for finding in findings:
        if finding.severity not in grouped:
            grouped[finding.severity] = []
        grouped[finding.severity].append(finding)
    has_error = False
    for severity in severity_order:
        if severity in grouped:
            for finding in grouped[severity]:
                print(f"[{finding.severity}] {finding.message}", file=sys.stderr)  # noqa: T201
            if severity == "ERROR":
                has_error = True
    if has_error:
        print("Harness validation failed", file=sys.stderr)  # noqa: T201
        return 1
    print("Harness validation passed")  # noqa: T201
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
