"""Validate the harness-engineering harness engineering validation surface."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


SHARED_MANIFEST_FIELDS = (
    "name",
    "description",
    "author",
    "repository",
    "homepage",
    "license",
    "skills",
)
OPTIONAL_SHARED_MANIFEST_FIELDS = ("keywords",)
CODEX_INTERFACE_FIELDS = (
    "displayName",
    "shortDescription",
    "longDescription",
    "developerName",
    "category",
    "capabilities",
    "defaultPrompt",
    "websiteURL",
)
CLAUDE_SCHEMA = "https://anthropic.com/claude-code/plugin.schema.json"
REQUIRED_STRUCTURE = (
    ".claude-plugin/plugin.json",
    ".codex-plugin/plugin.json",
    "README.md",
    "agents/architecture-guard.md",
    "agents/code-reviewer.md",
    "agents/doc-gardener.md",
    "agents/e2e-driver.md",
    "agents/spec-writer.md",
    "skills/harness-engineering/SKILL.md",
    "skills/harness-engineering/assets/CLAUDE.md.template",
    "skills/harness-engineering/assets/config.json.template",
    "skills/harness-engineering/assets/docs-directory-scaffold.md.template",
    "skills/harness-engineering/assets/execution-plan.md.template",
    "skills/harness-engineering/assets/git-hooks.md.template",
    "skills/harness-engineering/assets/github-actions.yml.template",
    "skills/harness-engineering/assets/gitignore.md.template",
    "skills/harness-engineering/assets/gitlab-ci.yml.template",
    "skills/harness-engineering/assets/harness-docs-update.md.template",
    "skills/harness-engineering/assets/node-validator/validate-harness.mjs.template",
    "skills/harness-engineering/assets/gradle-plugin/build.gradle.kts.template",
    "skills/harness-engineering/assets/gradle-plugin/HarnessCheckTask.kt.template",
    "skills/harness-engineering/assets/gradle-plugin/HarnessConventionPlugin.kt.template",
    "skills/harness-engineering/assets/maven-plugin/pom.xml.template",
    "skills/harness-engineering/assets/maven-plugin/src/main/java/com/example/harness/HarnessCheckMojo.java.template",
    "skills/harness-engineering/references/architecture-enforcement.md",
    "skills/harness-engineering/references/bootstrap-apply-flow.md",
    "skills/harness-engineering/references/ci-hooks-integration.md",
    "skills/harness-engineering/references/docs-as-context.md",
    "skills/harness-engineering/references/entropy-management.md",
    "skills/harness-engineering/references/source-verification.md",
    "skills/harness-engineering/scripts/validate_harness.py",
    "skills/harness-engineering/scripts/validate_harness.sh",
)


def load_json(path: Path) -> tuple[dict[str, Any] | None, list[str]]:
    """Load a JSON object and return validation errors instead of raising."""
    try:
        data = json.loads(path.read_text())
    except OSError as error:
        return None, [f"cannot read {path}: {error}"]
    except json.JSONDecodeError as error:
        return None, [f"invalid JSON in {path}: {error}"]
    if not isinstance(data, dict):
        return None, [f"{path} must contain a JSON object"]
    return data, []


def relative_path(path: Path, root: Path) -> str:
    """Return a stable POSIX-style path relative to the plugin root."""
    return path.relative_to(root).as_posix()


def validate_manifests(plugin_root: Path) -> list[str]:
    """Validate paired Claude and Codex manifests for this plugin."""
    errors: list[str] = []
    claude_path = plugin_root / ".claude-plugin" / "plugin.json"
    codex_path = plugin_root / ".codex-plugin" / "plugin.json"
    for path in (claude_path, codex_path):
        if not path.is_file():
            errors.append(f"missing manifest: {relative_path(path, plugin_root)}")
    if errors:
        return errors

    claude, claude_errors = load_json(claude_path)
    codex, codex_errors = load_json(codex_path)
    errors.extend(claude_errors)
    errors.extend(codex_errors)
    if claude is None or codex is None:
        return errors

    for field in SHARED_MANIFEST_FIELDS:
        if field not in claude:
            errors.append(f"Claude manifest missing shared field: {field}")
        if field not in codex:
            errors.append(f"Codex manifest missing shared field: {field}")
        if field in claude and field in codex and claude[field] != codex[field]:
            errors.append(f"manifest shared field differs: {field}")
    for field in OPTIONAL_SHARED_MANIFEST_FIELDS:
        if field in claude or field in codex:
            if claude.get(field) != codex.get(field):
                errors.append(f"manifest optional shared field differs: {field}")
    for runtime, manifest in (("Claude", claude), ("Codex", codex)):
        if "version" in manifest:
            errors.append(f"{runtime} manifest must not declare version")
        if "agents" in manifest:
            errors.append(f"{runtime} manifest must not declare agents")
        if manifest.get("skills") != "./skills/":
            errors.append(f"{runtime} manifest skills must be ./skills/")
    if claude.get("$schema") != CLAUDE_SCHEMA:
        errors.append("Claude manifest has an invalid or missing $schema")
    if "interface" in claude:
        errors.append("Claude manifest must not include interface")

    codex_interface = codex.get("interface")
    if not isinstance(codex_interface, dict):
        errors.append("Codex manifest must include an interface object")
    else:
        for field in CODEX_INTERFACE_FIELDS:
            if field not in codex_interface:
                errors.append(f"Codex interface missing field: {field}")
    return errors


def parse_frontmatter(path: Path) -> tuple[dict[str, str], list[str]]:
    """Parse scalar and folded YAML frontmatter fields used by agents and skills."""
    try:
        lines = path.read_text().splitlines()
    except OSError as error:
        return {}, [f"cannot read {path}: {error}"]
    if not lines or lines[0] != "---":
        return {}, [f"{path.name} is missing frontmatter"]

    frontmatter: dict[str, str] = {}
    index = 1
    while index < len(lines):
        line = lines[index]
        if line == "---":
            return frontmatter, []
        if ":" not in line or line.startswith((" ", "\t")):
            index += 1
            continue
        key, value = line.split(":", 1)
        key = key.strip()
        value = value.strip().strip("\"'")
        if value in (">-", "|-", ">", "|"):
            folded_lines: list[str] = []
            index += 1
            while index < len(lines) and (
                lines[index].startswith(" ") or not lines[index]
            ):
                folded_lines.append(lines[index].strip())
                index += 1
            frontmatter[key] = " ".join(line for line in folded_lines if line)
            continue
        frontmatter[key] = value
        index += 1
    return frontmatter, [f"{path.name} frontmatter is not closed"]


def validate_agents(plugin_root: Path) -> list[str]:
    """Validate plugin-root agents against basename and frontmatter rules."""
    agents_root = plugin_root / "agents"
    if not agents_root.exists():
        return []
    errors: list[str] = []
    for path in sorted(agents_root.glob("*.md")):
        frontmatter, parse_errors = parse_frontmatter(path)
        errors.extend(parse_errors)
        if frontmatter.get("name") != path.stem:
            errors.append(f"agent name must match file basename: agents/{path.name}")
        for field in ("description", "model", "color"):
            if not frontmatter.get(field):
                errors.append(
                    f"agent missing frontmatter field {field}: agents/{path.name}"
                )
    return errors


def validate_skills(plugin_root: Path) -> list[str]:
    """Validate skill entrypoints against directory names and required metadata."""
    skills_root = plugin_root / "skills"
    if not skills_root.exists():
        return ["missing skills directory"]
    errors: list[str] = []
    for skill_root in sorted(path for path in skills_root.iterdir() if path.is_dir()):
        skill_path = skill_root / "SKILL.md"
        if not skill_path.is_file():
            errors.append(
                f"missing skill entrypoint: skills/{skill_root.name}/SKILL.md"
            )
            continue
        frontmatter, parse_errors = parse_frontmatter(skill_path)
        errors.extend(parse_errors)
        if frontmatter.get("name") != skill_root.name:
            errors.append(
                f"skill name must match directory basename: skills/{skill_root.name}/SKILL.md"
            )
        if not frontmatter.get("description"):
            errors.append(
                f"skill missing frontmatter field description: skills/{skill_root.name}/SKILL.md"
            )
    return errors


def validate_structure(plugin_root: Path) -> list[str]:
    """Validate the harness-engineering plugin's expected file shape."""
    errors: list[str] = []
    for relative in REQUIRED_STRUCTURE:
        if not (plugin_root / relative).is_file():
            errors.append(f"missing required structure path: {relative}")
    for path in (plugin_root / "skills" / "harness-engineering" / "assets").rglob("*"):
        if path.is_file():
            relative = relative_path(path, plugin_root)
            if "-template." in path.name and not path.name.endswith(".template"):
                errors.append(
                    f"template filename must use .template suffix: {relative}"
                )
    return errors


def validate_plugin(plugin_root: Path) -> list[str]:
    """Validate only harness engineering metadata required for the plugin surface."""
    errors: list[str] = []
    errors.extend(validate_structure(plugin_root))
    errors.extend(validate_manifests(plugin_root))
    errors.extend(validate_agents(plugin_root))
    errors.extend(validate_skills(plugin_root))
    return errors


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--plugin-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Plugin root to validate.",
    )
    return parser.parse_args()


def main() -> int:
    """Run plugin validation and print actionable errors."""
    args = parse_args()
    plugin_root = args.plugin_root.resolve()
    errors = validate_plugin(plugin_root)
    if errors:
        for error in errors:
            print(f"error: {error}", file=sys.stderr)
        return 1
    print("harness-engineering plugin validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
