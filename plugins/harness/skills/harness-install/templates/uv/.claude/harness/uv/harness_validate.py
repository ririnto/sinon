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

ROOT = Path.cwd()
EXPECTED_VALIDATION_COMMAND = "uv run python .claude/harness/uv/harness_validate.py"
REQUIRED_FILES = [
    "AGENTS.md",
    "ARCHITECTURE.md",
    "CLAUDE.md",
    "docs/design-docs/index.md",
    "docs/design-docs/core-beliefs.md",
    "docs/exec-plans/tech-debt-tracker.md",
    "docs/product-specs/index.md",
    "docs/DESIGN.md",
    "docs/FRONTEND.md",
    "docs/PLANS.md",
    "docs/PRODUCT_SENSE.md",
    "docs/QUALITY_SCORE.md",
    "docs/RELIABILITY.md",
    "docs/SECURITY.md",
    ".claude/harness/git-hooks/pre-commit",
    ".claude/harness/git-hooks/pre-push",
]
REQUIRED_DIRECTORIES = [
    "docs",
    "docs/design-docs",
    "docs/exec-plans",
    "docs/exec-plans/active",
    "docs/exec-plans/completed",
    "docs/generated",
    "docs/product-specs",
    "docs/references",
    ".claude/agents",
    ".claude/skills",
    ".claude/harness/templates",
]
EMPTY_DIRECTORY_KEEP_FILES = [
    "docs/exec-plans/active/.gitkeep",
    "docs/exec-plans/completed/.gitkeep",
    "docs/generated/.gitkeep",
]
OPTIONAL_SEED_FILES = [
    "docs/product-specs/new-user-onboarding.md",
    "docs/references/design-system-reference-llms.txt",
    "docs/references/nixpacks-llms.txt",
    "docs/references/uv-llms.txt",
]
TEMPLATE_GROUPS = ["agent", "skill", "workflow", "ci", "docs"]
REQUIRED_DOC_HEADINGS = [
    "## Purpose",
    "## When To Update",
    "## Required Evidence",
    "## Validation Link",
]
REQUIRED_AUTHORED_DOCS = [
    path for path in REQUIRED_FILES if path.startswith("docs/") and path.endswith(".md")
]
LEAK_PATTERNS = [
    (re.compile(r"\{\{"), "unresolved template token"),
    (re.compile(r"(?m)^name:\s*example-"), "example frontmatter name"),
    (re.compile(r"Describe "), "scaffold prompt text"),
    (re.compile(r"\bTODO\b|\bTBD\b"), "TODO/TBD placeholder"),
    (re.compile(r"replace-with-stack-specific"), "stack placeholder"),
]


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


def is_safe_file(path: Path, failures: list[str]) -> bool:
    if path.is_symlink():
        if allowed_root_contract_target(path) is not None:
            return True
        failures.append(f"symlink file is not allowed: {relative(path)}")
        return False
    return path.is_file()


def is_safe_directory(path: Path, failures: list[str]) -> bool:
    if path.is_symlink():
        failures.append(f"symlink directory is not allowed: {relative(path)}")
        return False
    return path.is_dir()


def safe_walk(base: Path, failures: list[str]) -> list[Path]:
    if base.is_symlink():
        failures.append(f"symlink scan root is not allowed: {relative(base)}")
        return []
    if base.is_file():
        return [base]
    if not base.is_dir():
        return []
    output: list[Path] = []
    for current, directories, files in os.walk(base, followlinks=False):
        current_path = Path(current)
        kept_directories = []
        for name in directories:
            child = current_path / name
            if child.is_symlink():
                failures.append(f"symlink scan entry is not allowed: {relative(child)}")
            else:
                kept_directories.append(name)
        directories[:] = kept_directories
        for name in files:
            child = current_path / name
            if child.is_symlink():
                failures.append(f"symlink scan entry is not allowed: {relative(child)}")
            else:
                output.append(child)
    return output


def safe_file_or_walk(base: Path, failures: list[str]) -> list[Path]:
    if base.is_symlink() and allowed_root_contract_target(base) is None:
        failures.append(f"symlink path is not allowed: {relative(base)}")
        return []
    return [base] if is_safe_file(base, failures) else safe_walk(base, failures)


def load_manifest(failures: list[str]) -> dict[str, object]:
    path = ROOT / ".claude/harness/manifest.json"
    if path.is_symlink():
        failures.append("symlink file is not allowed: .claude/harness/manifest.json")
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except OSError:
        failures.append("missing file: .claude/harness/manifest.json")
    except json.JSONDecodeError as error:
        failures.append(f"invalid JSON: .claude/harness/manifest.json: {error}")
    return {}


def manifest_list(manifest: dict[str, object], key: str) -> list[str]:
    value = manifest.get(key)
    return (
        [item for item in value if isinstance(item, str)]
        if isinstance(value, list)
        else []
    )


def compare_manifest_list(
    failures: list[str], manifest: dict[str, object], key: str, expected: list[str]
) -> None:
    actual = manifest_list(manifest, key)
    if sorted(actual) != sorted(expected):
        failures.append(f"manifest {key} must match validator constants")


def validate() -> list[str]:
    failures: list[str] = []
    manifest = load_manifest(failures)
    compare_manifest_list(failures, manifest, "requiredFiles", REQUIRED_FILES)
    compare_manifest_list(
        failures, manifest, "requiredDirectories", REQUIRED_DIRECTORIES
    )
    compare_manifest_list(
        failures, manifest, "emptyDirectoryKeepFiles", EMPTY_DIRECTORY_KEEP_FILES
    )
    compare_manifest_list(failures, manifest, "optionalSeedFiles", OPTIONAL_SEED_FILES)
    compare_manifest_list(failures, manifest, "templateGroups", TEMPLATE_GROUPS)
    for path in REQUIRED_FILES:
        if not is_safe_file(ROOT / path, failures):
            failures.append(f"missing file: {path}")
    for path in REQUIRED_DIRECTORIES:
        if not is_safe_directory(ROOT / path, failures):
            failures.append(f"missing directory: {path}")
    validate_keep_files(failures)
    validate_docs(failures)
    agents_text = read_text(ROOT / "AGENTS.md")
    claude_text = read_text(ROOT / "CLAUDE.md")
    generated_text = "\n".join(
        [agents_text, claude_text, read_text(ROOT / "ARCHITECTURE.md")]
    )
    evolution_text = "\n".join(
        [agents_text, claude_text, read_text(ROOT / ".claude/harness/evolution-log.md")]
    )
    if "Repository Harness Contract" not in agents_text:
        failures.append("AGENTS.md must contain Repository Harness Contract")
    if "Claude Code Entry Point" not in claude_text:
        failures.append("CLAUDE.md must contain Claude Code Entry Point")
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
    validate_agents(failures)
    validate_skills(failures)
    validate_templates(failures)
    validate_active_assets(failures)
    validate_hooks(failures)
    validate_env_shebangs(failures)
    return failures


def validate_keep_files(failures: list[str]) -> None:
    """Validate that empty directories contain .gitkeep placeholder files."""
    for keep in EMPTY_DIRECTORY_KEEP_FILES:
        keep_path = ROOT / keep
        directory = keep_path.parent
        if not is_safe_directory(directory, failures):
            continue
        real_files = [path for path in directory.iterdir() if path.name != ".gitkeep"]
        if not real_files and not is_safe_file(keep_path, failures):
            failures.append(
                f"empty directory must keep placeholder or real files: {relative(directory)}"
            )


def validate_docs(failures: list[str]) -> None:
    """Validate that required documentation files contain mandatory headings."""
    for doc in REQUIRED_AUTHORED_DOCS:
        path = ROOT / doc
        if not is_safe_file(path, failures):
            continue
        text = read_text(path)
        for heading in REQUIRED_DOC_HEADINGS:
            if heading not in text:
                failures.append(f"doc missing {heading}: {doc}")


def validate_agents(failures: list[str]) -> None:
    """Validate that agent files contain required frontmatter fields."""
    directory = ROOT / ".claude/agents"
    files = sorted(
        path
        for path in safe_walk(directory, failures)
        if path.parent == directory and path.suffix == ".md"
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


def validate_skills(failures: list[str]) -> None:
    """Validate that skill SKILL.md files contain required frontmatter fields."""
    directory = ROOT / ".claude/skills"
    files = sorted(
        path for path in safe_walk(directory, failures) if path.name == "SKILL.md"
    )
    if not files:
        failures.append(".claude/skills must contain at least one SKILL.md")
    for path in files:
        text = read_text(path)
        if not text.startswith("---"):
            failures.append(f"skill missing frontmatter: {relative(path)}")
        if not re.search(r"(?m)^description:\s*.+$", text):
            failures.append(f"skill missing description: {relative(path)}")


def validate_templates(failures: list[str]) -> None:
    """Validate that all required template groups exist."""
    for group in TEMPLATE_GROUPS:
        if not is_safe_directory(ROOT / f".claude/harness/templates/{group}", failures):
            failures.append(
                f"missing template group: .claude/harness/templates/{group}"
            )


def validate_active_assets(failures: list[str]) -> None:
    """Validate that active assets don't contain placeholder or unresolved tokens."""
    excluded = ROOT / ".claude/harness/templates"
    active_roots = [
        ROOT / "AGENTS.md",
        ROOT / "CLAUDE.md",
        ROOT / "ARCHITECTURE.md",
        ROOT / "docs",
        ROOT / ".claude/agents",
        ROOT / ".claude/skills",
        ROOT / ".claude/harness",
        ROOT / ".github",
    ]
    for base in active_roots:
        paths = safe_file_or_walk(base, failures)
        for path in paths:
            if (
                not path.is_file()
                or excluded in path.parents
                or path.suffix not in {".md", ".txt", ".json", ".yml", ".yaml"}
            ):
                continue
            text = read_text(path)
            for pattern, label in LEAK_PATTERNS:
                if pattern.search(text):
                    failures.append(f"{label} in active asset: {relative(path)}")


def hook_command(pre_push_text: str) -> str:
    """Extract the Harness validation command from pre-push hook text."""
    for line in pre_push_text.splitlines():
        if line.startswith("# Harness validation command: "):
            return line.removeprefix("# Harness validation command: ").strip()
    return ""


def validate_one_hook(failures: list[str], name: str, stage: str) -> str:
    """Validate a single Git hook file for required content and structure."""
    hook = ROOT / f".claude/harness/git-hooks/{name}"
    hook_text = ""
    if is_safe_file(hook, failures):
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
    return hook_text


def validate_hooks(failures: list[str]) -> None:
    """Validate Git hooks for correct commands and configuration."""
    pre_commit_text = validate_one_hook(failures, "pre-commit", "compliance")
    pre_push_text = validate_one_hook(failures, "pre-push", "full-validation")
    if re.search(
        r"(^|\s)(uv|bun|gradle|mvn)(\s|$)|\./gradlew|harnessValidate|"
        r"harness_validate\.py|harness-validate\.ts",
        pre_commit_text,
    ):
        failures.append("pre-commit hook must not run full stack validation commands")
    command = hook_command(pre_push_text)
    if not command:
        failures.append("pre-push hook must declare Harness validation command")
        return
    if command != EXPECTED_VALIDATION_COMMAND:
        failures.append(
            f"pre-push hook declares unsupported validation command: {command}"
        )
        return
    if command not in pre_push_text.splitlines():
        failures.append("pre-push hook must run the declared validation command")
    for ci_file in [".github/workflows/harness.yml", ".gitlab-ci.yml"]:
        path = ROOT / ci_file
        if (
            path.exists()
            and is_safe_file(path, failures)
            and command not in read_text(path)
        ):
            failures.append(f"{ci_file}: CI command mismatch - expected {command}")


def validate_env_shebangs(failures: list[str]) -> None:
    """Validate that executable scripts use /usr/bin/env shebangs."""
    for base in [ROOT / ".claude/harness", ROOT / ".claude/skills"]:
        if not base.is_dir():
            continue
        for path in safe_walk(base, failures):
            if not path.is_file() or not is_executable(path):
                continue
            line = first_line(path)
            if line.startswith("#!") and not line.startswith("#!/usr/bin/env "):
                failures.append(
                    f"executable script should use /usr/bin/env shebang: {relative(path)}"
                )


def main() -> int:
    """Run all validations and report results."""
    failures = validate()
    if failures:
        print("Harness validation failed:", file=sys.stderr)  # noqa: T201
        for failure in dict.fromkeys(failures):
            print(f"- {failure}", file=sys.stderr)  # noqa: T201
        return 1
    print("Harness validation passed")  # noqa: T201
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
