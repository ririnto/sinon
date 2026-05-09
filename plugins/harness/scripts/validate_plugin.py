#!/usr/bin/env -S uv run
# /// script
# dependencies = []
# ///

"""Validate the harness plugin validation surface."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


CLAUDE_MANIFEST_FIELDS = (
    "name",
    "description",
    "author",
    "repository",
    "homepage",
    "license",
    "skills",
)
CLAUDE_SCHEMA = "https://anthropic.com/claude-code/plugin.schema.json"
REMOVED_PLUGIN_METADATA_DIR = "." + "co" + "dex-plugin"
REMOVED_SKILL_AGENT_METADATA = str(Path("agents") / ("openai" + ".yaml"))
REQUIRED_STRUCTURE = (
    ".claude-plugin/plugin.json",
    "README.md",
    "scripts/validate_plugin.py",
    "agents/architecture-guard.md",
    "agents/code-reviewer.md",
    "agents/doc-gardener.md",
    "agents/e2e-driver.md",
    "agents/spec-writer.md",
    "skills/setup-harness/SKILL.md",
    "skills/setup-harness/assets/CLAUDE.md.template",
    "skills/setup-harness/assets/config.json.template",
    "skills/setup-harness/assets/docs-directory-scaffold.md.template",
    "skills/setup-harness/assets/execution-plan.md.template",
    "skills/setup-harness/assets/git-hooks.md.template",
    "skills/setup-harness/assets/github-actions.yml.template",
    "skills/setup-harness/assets/gitignore.md.template",
    "skills/setup-harness/assets/gitlab-ci.yml.template",
    "skills/setup-harness/assets/harness-docs-update.md.template",
    "skills/setup-harness/assets/proof-packet.md.template",
    "skills/setup-harness/assets/spec.md.template",
    "skills/setup-harness/assets/workflow.md.template",
    "skills/setup-harness/assets/bun-validator/validate-harness.mjs.template",
    "skills/setup-harness/assets/gradle-plugin/build.gradle.kts.template",
    "skills/setup-harness/assets/gradle-plugin/HarnessCheckTask.kt.template",
    "skills/setup-harness/assets/gradle-plugin/HarnessConventionPlugin.kt.template",
    "skills/setup-harness/assets/maven-plugin/pom.xml.template",
    "skills/setup-harness/assets/maven-plugin/src/main/java/com/example/harness/HarnessCheckMojo.java.template",
    "skills/setup-harness/references/architecture-enforcement.md",
    "skills/setup-harness/references/bootstrap-apply-flow.md",
    "skills/setup-harness/references/ci-hooks-integration.md",
    "skills/setup-harness/references/docs-as-context.md",
    "skills/setup-harness/references/entropy-management.md",
    "skills/setup-harness/references/source-verification.md",
    "skills/setup-harness/references/symphony-service-specification.md",
    "skills/setup-harness/scripts/validate_harness.py",
    "skills/setup-harness/scripts/validate_harness.sh",
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
    """Validate the Claude-only harness plugin manifest contract."""
    errors: list[str] = []
    claude_path = plugin_root / ".claude-plugin" / "plugin.json"
    if not claude_path.is_file():
        errors.append(f"missing manifest: {relative_path(claude_path, plugin_root)}")
    if errors:
        return errors

    claude, claude_errors = load_json(claude_path)
    errors.extend(claude_errors)
    if claude is None:
        return errors

    for field in CLAUDE_MANIFEST_FIELDS:
        if field not in claude:
            errors.append(f"Claude manifest missing field: {field}")
    if "version" in claude:
        errors.append("Claude manifest must not declare version")
    if "agents" in claude:
        errors.append("Claude manifest must not declare agents")
    if claude.get("skills") != "./skills/":
        errors.append("Claude manifest skills must be ./skills/")
    if claude.get("$schema") != CLAUDE_SCHEMA:
        errors.append("Claude manifest has an invalid or missing $schema")
    if "interface" in claude:
        errors.append("Claude manifest must not include interface")
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
    """Validate the harness plugin's expected file shape."""
    errors: list[str] = []
    for path in sorted(plugin_root.rglob(REMOVED_PLUGIN_METADATA_DIR)):
        errors.append(
            f"stale plugin metadata must not be present: {relative_path(path, plugin_root)}"
        )
    for path in sorted(plugin_root.rglob(REMOVED_SKILL_AGENT_METADATA)):
        errors.append(
            f"stale skill-local agent metadata must not be present: {relative_path(path, plugin_root)}"
        )
    for relative in REQUIRED_STRUCTURE:
        if not (plugin_root / relative).is_file():
            errors.append(f"missing required structure path: {relative}")
    for path in (plugin_root / "skills" / "setup-harness" / "assets").rglob("*"):
        if path.is_file():
            relative = relative_path(path, plugin_root)
            if "-template." in path.name and not path.name.endswith(".template"):
                errors.append(
                    f"template filename must use .template suffix: {relative}"
                )
    for pycache in plugin_root.rglob("__pycache__"):
        errors.append(f"cache directory must not be present: {relative_path(pycache, plugin_root)}")
    return errors


def validate_plugin(plugin_root: Path) -> list[str]:
    """Validate only harness metadata required for the plugin surface."""
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
    print("harness plugin validation passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
