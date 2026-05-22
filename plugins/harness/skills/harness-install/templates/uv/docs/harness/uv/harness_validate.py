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


def manifest_list(manifest: dict[str, object], key: str) -> tuple[str, ...]:
    value = manifest.get(key)
    return (
        tuple(item for item in value if isinstance(item, str))
        if isinstance(value, list)
        else ()
    )


def build_leak_patterns(manifest: dict[str, object]) -> tuple[LeakPattern, ...]:
    patterns_data = manifest.get("leakPatterns")
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


def validate_manifest_exists() -> tuple[str, ...]:
    path = ROOT / MANIFEST_PATH
    if path.is_symlink():
        return ("manifest file is a symlink; must be a regular file",)
    if not is_safe_file(path):
        return (f"manifest file missing: {MANIFEST_PATH}",)
    if not load_manifest():
        return (f"manifest file invalid or empty JSON: {MANIFEST_PATH}",)
    return ()


def validate_structure(manifest: dict[str, object]) -> tuple[str, ...]:
    failures: list[str] = []
    required_files = manifest_list(manifest, "requiredFiles")
    required_dirs = manifest_list(manifest, "requiredDirectories")
    keep_files = manifest_list(manifest, "emptyDirectoryKeepFiles")
    for path in required_files:
        if not is_safe_file(ROOT / path):
            failures.append(f"missing file: {path}")
    for path in required_dirs:
        if not is_safe_directory(ROOT / path):
            failures.append(f"missing directory: {path}")
    for keep in keep_files:
        keep_path = ROOT / keep
        directory = keep_path.parent
        if not is_safe_directory(directory):
            continue
        real_files = [p for p in directory.iterdir() if p.name != ".gitkeep"]
        if not real_files and not is_safe_file(keep_path):
            failures.append(
                f"empty directory must keep placeholder or real files: {relative(directory)}"
            )
    return tuple(failures)


def validate_docs_headings(manifest: dict[str, object]) -> tuple[str, ...]:
    failures: list[str] = []
    required_docs = manifest_list(manifest, "requiredFiles")
    required_headings = manifest_list(manifest, "requiredDocHeadings")
    for doc in required_docs:
        if not doc.startswith("docs/") or not doc.endswith(".md"):
            continue
        path = ROOT / doc
        if not is_safe_file(path):
            continue
        text = read_text(path)
        for heading in required_headings:
            if heading not in text:
                failures.append(f"doc missing {heading}: {doc}")
    return tuple(failures)


def validate_content_checks(manifest: dict[str, object]) -> tuple[str, ...]:
    failures: list[str] = []
    checks_data = manifest.get("requiredContentChecks")
    if not isinstance(checks_data, list):
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
            failures.append(failure_msg)
    return tuple(failures)


def validate_agents() -> tuple[str, ...]:
    failures: list[str] = []
    directory = ROOT / ".claude/agents"
    files = tuple(
        sorted(
            path
            for path in safe_walk(directory)
            if path.parent == directory and path.suffix == ".md"
        )
    )
    if not files:
        failures.append(".claude/agents must contain at least one .md agent")
    for path in files:
        text = read_text(path)
        if not text.startswith("---"):
            failures.append(f"agent missing frontmatter: {relative(path)}")
        if not re.search(r"(?m)^name:\s*[-a-z0-9]+\s*$", text):
            failures.append(f"agent missing name: {relative(path)}")
        if not re.search(r"(?m)^description:\s*.+$", text):
            failures.append(f"agent missing description: {relative(path)}")
    return tuple(failures)


def validate_skills() -> tuple[str, ...]:
    failures: list[str] = []
    directory = ROOT / ".claude/skills"
    files = tuple(
        sorted(path for path in safe_walk(directory) if path.name == "SKILL.md")
    )
    if not files:
        failures.append(".claude/skills must contain at least one SKILL.md")
    for path in files:
        text = read_text(path)
        if not text.startswith("---"):
            failures.append(f"skill missing frontmatter: {relative(path)}")
        if not re.search(r"(?m)^description:\s*.+$", text):
            failures.append(f"skill missing description: {relative(path)}")
    return tuple(failures)


def validate_templates(manifest: dict[str, object]) -> tuple[str, ...]:
    failures: list[str] = []
    template_groups = manifest_list(manifest, "templateGroups")
    for group in template_groups:
        if not is_safe_directory(ROOT / f"docs/harness/templates/{group}"):
            failures.append(f"missing template group: docs/harness/templates/{group}")
    return tuple(failures)


def validate_active_assets(manifest: dict[str, object]) -> tuple[str, ...]:
    failures: list[str] = []
    bases_data = manifest.get("activeAssetBases")
    excluded_data = manifest.get("excludedActiveAssetSubtrees")
    exts_data = manifest.get("activeAssetExtensions")
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
                    failures.append(f"{leak.label} in active asset: {relative(path)}")
    return tuple(failures)


def hook_command(pre_push_text: str) -> str:
    for line in pre_push_text.splitlines():
        if line.startswith("# Harness validation command: "):
            return line.removeprefix("# Harness validation command: ").strip()
    return ""


def validate_one_hook(
    name: str, stage: str, manifest: dict[str, object]
) -> tuple[str, tuple[str, ...]]:
    hook = ROOT / f"docs/harness/git-hooks/{name}"
    hook_text = ""
    failures: list[str] = []
    if is_safe_file(hook):
        hook_text = read_text(hook)
        if first_line(hook) != "#!/usr/bin/env sh":
            failures.append(f"{name} hook must use #!/usr/bin/env sh")
        if not is_executable(hook):
            failures.append(f"{name} hook must be executable: {relative(hook)}")
        if f"Harness generated hook: {name}" not in hook_text:
            failures.append(f"{name} hook must contain generated marker")
        if f"Harness stage: {stage}" not in hook_text:
            failures.append(f"{name} hook must contain {stage} stage marker")
        if "packaged placeholder is replaced during harness installation" in hook_text:
            failures.append(
                f"{name} hook must be installer-generated selected-mode content"
            )
    return hook_text, tuple(failures)


def validate_hooks(manifest: dict[str, object]) -> tuple[str, ...]:
    failures: list[str] = []
    hook_stages_data = manifest.get("hookStages")
    expected_commands_data = manifest.get("expectedValidationCommands")
    if not isinstance(hook_stages_data, dict) or not isinstance(
        expected_commands_data, dict
    ):
        return ("hook stages or validation commands missing from manifest",)
    stack_stages = hook_stages_data.get(STACK)
    if not isinstance(stack_stages, dict):
        return (f"hook stages for stack '{STACK}' missing from manifest",)
    expected_command_obj = expected_commands_data.get(STACK)
    if not isinstance(expected_command_obj, str):
        return (f"validation command for stack '{STACK}' missing from manifest",)
    pre_commit_stage = stack_stages.get("preCommit")
    pre_push_stage = stack_stages.get("prePush")
    if not isinstance(pre_commit_stage, str) or not isinstance(pre_push_stage, str):
        return (f"hook stage values for '{STACK}' must be strings",)
    pre_commit_text, pre_commit_failures = validate_one_hook(
        "pre-commit", pre_commit_stage, manifest
    )
    failures.extend(pre_commit_failures)
    pre_push_text, pre_push_failures = validate_one_hook(
        "pre-push", pre_push_stage, manifest
    )
    failures.extend(pre_push_failures)
    if re.search(
        r"(^|\s)(uv|bun|gradle|mvn)(\s|$)|\./gradlew|harnessValidate|"
        r"harness_validate\.py|harness-validate\.ts",
        pre_commit_text,
    ):
        failures.append("pre-commit hook must not run full stack validation commands")
    command = hook_command(pre_push_text)
    if not command:
        failures.append("pre-push hook must declare Harness validation command")
        return tuple(failures)
    if command != expected_command_obj:
        failures.append(
            f"pre-push hook declares unsupported validation command: {command}"
        )
        return tuple(failures)
    if command not in pre_push_text.splitlines():
        failures.append("pre-push hook must run the declared validation command")
    for ci_file in [".github/workflows/harness.yml", ".gitlab-ci.yml"]:
        path = ROOT / ci_file
        if (
            path.exists()
            and is_safe_file(path)
            and command not in read_text(path)
        ):
            failures.append(f"{ci_file}: CI command mismatch - expected {command}")
    return tuple(failures)


def validate_env_shebangs(manifest: dict[str, object]) -> tuple[str, ...]:
    failures: list[str] = []
    shebang_bases_data = manifest.get("envShebangBases")
    if not isinstance(shebang_bases_data, list):
        return ()
    bases = tuple(ROOT / item for item in shebang_bases_data if isinstance(item, str))
    for base in bases:
        if not base.is_dir():
            continue
        for path in safe_walk(base):
            if not path.is_file() or not is_executable(path):
                continue
            line = first_line(path)
            if line.startswith("#!") and not line.startswith("#!/usr/bin/env "):
                failures.append(
                    f"executable script should use /usr/bin/env shebang: {relative(path)}"
                )
    return tuple(failures)


def validate_completed_plans(manifest: dict[str, object]) -> tuple[str, ...]:
    failures: list[str] = []
    completed_dir_name = manifest.get("completedPlanDirectory")
    unfinished_pattern_str = manifest.get("unfinishedTaskPattern")
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
        return (f"invalid unfinishedTaskPattern regex: {unfinished_pattern_str}",)
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
            failures.append(f"completed plan has unchecked tasks: {relative(path)}")
    return tuple(failures)


def validate() -> tuple[str, ...]:
    manifest_exists_failures = validate_manifest_exists()
    if manifest_exists_failures:
        return manifest_exists_failures
    manifest = load_manifest()
    if not manifest:
        return ("manifest is empty",)
    all_failures: list[tuple[str, ...]] = [
        validate_structure(manifest),
        validate_docs_headings(manifest),
        validate_content_checks(manifest),
        validate_agents(),
        validate_skills(),
        validate_templates(manifest),
        validate_active_assets(manifest),
        validate_hooks(manifest),
        validate_env_shebangs(manifest),
        validate_completed_plans(manifest),
    ]
    combined = tuple(f for failures in all_failures for f in failures)
    return tuple(dict.fromkeys(combined))


def main() -> int:
    failures = validate()
    if failures:
        print("Harness validation failed:", file=sys.stderr)  # noqa: T201
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)  # noqa: T201
        return 1
    print("Harness validation passed")  # noqa: T201
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
