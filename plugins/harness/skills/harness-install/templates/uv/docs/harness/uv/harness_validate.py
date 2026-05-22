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
EXPECTED_VALIDATION_COMMAND = "uv run python docs/harness/uv/harness_validate.py"
REQUIRED_FILES = (
    "AGENTS.md",
    "ARCHITECTURE.md",
    "CLAUDE.md",
    "docs/design-docs/core-beliefs.md",
    "docs/exec-plans/tech-debt-tracker.md",
    "docs/DESIGN.md",
    "docs/FRONTEND.md",
    "docs/PLANS.md",
    "docs/PRODUCT_SENSE.md",
    "docs/QUALITY_SCORE.md",
    "docs/RELIABILITY.md",
    "docs/SECURITY.md",
    "docs/harness/git-hooks/pre-commit",
    "docs/harness/git-hooks/pre-push",
)
REQUIRED_DIRECTORIES = (
    "docs",
    "docs/design-docs",
    "docs/exec-plans",
    "docs/exec-plans/active",
    "docs/exec-plans/completed",
    "docs/generated",
    "docs/harness",
    "docs/harness/templates",
    "docs/product-specs",
    "docs/references",
    ".claude/agents",
    ".claude/skills",
)
EMPTY_DIRECTORY_KEEP_FILES = (
    "docs/exec-plans/active/.gitkeep",
    "docs/exec-plans/completed/.gitkeep",
    "docs/generated/.gitkeep",
)
OPTIONAL_SEED_FILES = (
    "docs/product-specs/new-user-onboarding.md",
)
TEMPLATE_GROUPS = ("agent", "skill", "workflow", "ci", "docs")
REQUIRED_DOC_HEADINGS = (
    "## Purpose",
    "## When To Update",
    "## Required Evidence",
)
REQUIRED_AUTHORED_DOCS = tuple(
    path for path in REQUIRED_FILES if path.startswith("docs/") and path.endswith(".md")
)


class LeakPattern(NamedTuple):
    """Represents a pattern to detect in active assets."""

    pattern: re.Pattern[str]
    label: str


LEAK_PATTERNS = (
    LeakPattern(re.compile(r"\{\{"), "unresolved template token"),
    LeakPattern(re.compile(r"(?m)^name:\s*example-"), "example frontmatter name"),
    LeakPattern(re.compile(r"Describe "), "scaffold prompt text"),
    LeakPattern(re.compile(r"\bTODO\b|\bTBD\b"), "TODO/TBD placeholder"),
    LeakPattern(re.compile(r"replace-with-stack-specific"), "stack placeholder"),
)


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
    path = ROOT / "docs/harness/manifest.json"
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


def validate_manifest_parity() -> Iterable[str]:
    manifest = load_manifest()
    failures: list[str] = []
    actual_files = manifest_list(manifest, "requiredFiles")
    if sorted(actual_files) != sorted(REQUIRED_FILES):
        failures.append("manifest requiredFiles must match validator constants")
    actual_dirs = manifest_list(manifest, "requiredDirectories")
    if sorted(actual_dirs) != sorted(REQUIRED_DIRECTORIES):
        failures.append("manifest requiredDirectories must match validator constants")
    actual_keeps = manifest_list(manifest, "emptyDirectoryKeepFiles")
    if sorted(actual_keeps) != sorted(EMPTY_DIRECTORY_KEEP_FILES):
        failures.append("manifest emptyDirectoryKeepFiles must match validator constants")
    actual_seeds = manifest_list(manifest, "optionalSeedFiles")
    if sorted(actual_seeds) != sorted(OPTIONAL_SEED_FILES):
        failures.append("manifest optionalSeedFiles must match validator constants")
    actual_groups = manifest_list(manifest, "templateGroups")
    if sorted(actual_groups) != sorted(TEMPLATE_GROUPS):
        failures.append("manifest templateGroups must match validator constants")
    return failures


def validate_structure() -> Iterable[str]:
    failures: list[str] = []
    for path in REQUIRED_FILES:
        if not is_safe_file(ROOT / path):
            failures.append(f"missing file: {path}")
    for path in REQUIRED_DIRECTORIES:
        if not is_safe_directory(ROOT / path):
            failures.append(f"missing directory: {path}")
    for keep in EMPTY_DIRECTORY_KEEP_FILES:
        keep_path = ROOT / keep
        directory = keep_path.parent
        if not is_safe_directory(directory):
            continue
        real_files = [path for path in directory.iterdir() if path.name != ".gitkeep"]
        if not real_files and not is_safe_file(keep_path):
            failures.append(
                f"empty directory must keep placeholder or real files: {relative(directory)}"
            )
    return failures


def validate_docs() -> Iterable[str]:
    failures: list[str] = []
    for doc in REQUIRED_AUTHORED_DOCS:
        path = ROOT / doc
        if not is_safe_file(path):
            continue
        text = read_text(path)
        for heading in REQUIRED_DOC_HEADINGS:
            if heading not in text:
                failures.append(f"doc missing {heading}: {doc}")
    return failures


def validate_content() -> Iterable[str]:
    failures: list[str] = []
    agents_text = read_text(ROOT / "AGENTS.md")
    claude_text = read_text(ROOT / "CLAUDE.md")
    generated_text = "\n".join(
        [agents_text, claude_text, read_text(ROOT / "ARCHITECTURE.md")]
    )
    evolution_text = "\n".join(
        [agents_text, claude_text, read_text(ROOT / "docs/harness/evolution-log.md")]
    )
    if "Repository Harness Contract" not in agents_text:
        failures.append("AGENTS.md must contain Repository Harness Contract")
    if "## Entry Point" not in claude_text:
        failures.append("CLAUDE.md must contain an Entry Point section")
    if "AGENTS.md" not in claude_text:
        failures.append("CLAUDE.md must reference AGENTS.md")
    if "docs/generated/" not in agents_text:
        failures.append("AGENTS.md must describe docs/generated/ semantics")
    if "docs/generated/db-schema.md" not in generated_text:
        failures.append(
            "repository docs must state that docs/generated/db-schema.md is only an example, not a required scaffold file"
        )
    if (
        "source command" not in generated_text
        or "regeneration trigger" not in generated_text
    ):
        failures.append(
            "repository docs must describe generated-artifact source command and regeneration trigger metadata"
        )
    if "discovery" not in evolution_text or "maintenance" not in evolution_text:
        failures.append(
            "repository docs must state that the harness may evolve across development phases"
        )
    return failures


def validate_agents() -> Iterable[str]:
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
    return failures


def validate_skills() -> Iterable[str]:
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
    return failures


def validate_templates() -> Iterable[str]:
    failures: list[str] = []
    for group in TEMPLATE_GROUPS:
        if not is_safe_directory(ROOT / f"docs/harness/templates/{group}"):
            failures.append(f"missing template group: docs/harness/templates/{group}")
    return failures


def validate_active_assets() -> Iterable[str]:
    failures: list[str] = []
    excluded = ROOT / "docs/harness/templates"
    active_roots = (
        ROOT / "AGENTS.md",
        ROOT / "CLAUDE.md",
        ROOT / "ARCHITECTURE.md",
        ROOT / "docs",
        ROOT / ".claude/agents",
        ROOT / ".claude/skills",
        ROOT / "docs/harness",
        ROOT / ".github",
    )
    for base in active_roots:
        paths = safe_file_or_walk(base)
        for path in paths:
            if (
                not path.is_file()
                or excluded in path.parents
                or path.suffix not in {".md", ".txt", ".json", ".yml", ".yaml"}
            ):
                continue
            text = read_text(path)
            for leak in LEAK_PATTERNS:
                if leak.pattern.search(text):
                    failures.append(f"{leak.label} in active asset: {relative(path)}")
    return failures


def hook_command(pre_push_text: str) -> str:
    for line in pre_push_text.splitlines():
        if line.startswith("# Harness validation command: "):
            return line.removeprefix("# Harness validation command: ").strip()
    return ""


def validate_one_hook(name: str, stage: str) -> tuple[str, Iterable[str]]:
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
    return hook_text, failures


def validate_hooks() -> Iterable[str]:
    failures: list[str] = []
    pre_commit_text, pre_commit_failures = validate_one_hook("pre-commit", "compliance")
    failures.extend(pre_commit_failures)
    pre_push_text, pre_push_failures = validate_one_hook("pre-push", "full-validation")
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
        return failures
    if command != EXPECTED_VALIDATION_COMMAND:
        failures.append(
            f"pre-push hook declares unsupported validation command: {command}"
        )
        return failures
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
    return failures


def validate_env_shebangs() -> Iterable[str]:
    failures: list[str] = []
    for base in [ROOT / "docs/harness", ROOT / ".claude/skills"]:
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
    return failures


def validate_completed_plans() -> Iterable[str]:
    failures: list[str] = []
    completed_dir = ROOT / "docs/exec-plans/completed"
    if not is_safe_directory(completed_dir):
        return failures
    files = tuple(
        sorted(
            path
            for path in completed_dir.iterdir()
            if path.is_file() and path.suffix == ".md" and path.name != ".gitkeep"
        )
    )
    for path in files:
        text = read_text(path)
        if re.search(r"^\s*-\s*\[ \]\s", text, re.MULTILINE):
            failures.append(f"completed plan has unchecked tasks: {relative(path)}")
    return failures


def validate() -> tuple[str, ...]:
    all_failures: list[Iterable[str]] = [
        validate_manifest_parity(),
        validate_structure(),
        validate_docs(),
        validate_content(),
        validate_agents(),
        validate_skills(),
        validate_templates(),
        validate_active_assets(),
        validate_hooks(),
        validate_env_shebangs(),
        validate_completed_plans(),
    ]
    combined = (failure for failures in all_failures for failure in failures)
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
