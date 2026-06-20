"""Repository inventory validation for Sinon plugin packages."""

from __future__ import annotations

import re
from pathlib import Path

from package_checks_common import (
    CLAUDE_AGENT_POINTER,
    KEBAB_RE,
    MARKETPLACE_SCHEMA,
    JsonValue,
    Reporter,
    extract_section,
    parse_frontmatter,
    read_json,
    skill_names,
    table_first_column_names,
)
from package_checks_manifest import validate_manifest


ROOT_LINKS = {
    ".claude/agents": "plugins/agent-capability-kit/agents",
    ".claude/skills": "plugins/agent-capability-kit/skills",
    ".agents/skills": ".claude/skills",
    ".codex/agents": ".claude/agents",
}
BLOCKED_ROOT_PATHS = {
    ".agents/agents": "agents are exposed through .codex/agents",
}


def path_exists(path: Path) -> bool:
    """Return true for existing paths and broken symlinks."""
    return path.exists() or path.is_symlink()


def resolve_existing(path: Path) -> Path | None:
    """Resolve a path that must exist."""
    try:
        return path.resolve(strict=True)
    except (OSError, RuntimeError):
        return None


def validate_root_link(
    root: Path,
    link: str,
    target: str,
    reporter: Reporter,
) -> None:
    """Validate a root symlink by resolved destination."""
    link_path = root / link
    target_path = root / target
    if not path_exists(link_path):
        reporter.error(link_path, f"required symlink `-> {target}` is missing")
        return
    if not link_path.is_symlink():
        reporter.error(link_path, f"must be symlink `-> {target}`")
        return
    actual = resolve_existing(link_path)
    expected = resolve_existing(target_path)
    if actual is None:
        reporter.error(link_path, f"cannot resolve symlink `-> {target}`")
        return
    if expected is None:
        reporter.error(target_path, "target path cannot be resolved")
        return
    if actual != expected:
        reporter.error(link_path, f"must resolve to {target}")


def validate_root_link_layout(root: Path, reporter: Reporter) -> None:
    """Validate root compatibility symlinks for agent runtimes."""
    for link, target in ROOT_LINKS.items():
        validate_root_link(root, link, target, reporter)
    for path, reason in BLOCKED_ROOT_PATHS.items():
        blocked_path = root / path
        if path_exists(blocked_path):
            reporter.error(blocked_path, f"must not exist; {reason}")


def validate_skills(plugin_root: Path, reporter: Reporter) -> None:
    """Validate packaged skill frontmatter."""
    for name in skill_names(plugin_root):
        skill_path = plugin_root / "skills" / name / "SKILL.md"
        frontmatter = parse_frontmatter(skill_path, reporter)
        if frontmatter.get("name") != name:
            reporter.error(
                skill_path, "frontmatter name must match skill directory basename"
            )
        if not frontmatter.get("description"):
            reporter.error(skill_path, "frontmatter description is required")


def validate_agents(plugin_root: Path, reporter: Reporter) -> None:
    """Validate packaged agent frontmatter."""
    agents_dir = plugin_root / "agents"
    if not agents_dir.is_dir():
        return
    for agent_path in sorted(agents_dir.glob("*.md")):
        frontmatter = parse_frontmatter(agent_path, reporter)
        if not KEBAB_RE.fullmatch(agent_path.stem):
            reporter.error(agent_path, "agent filename stem must use kebab-case")
        if frontmatter.get("name") != agent_path.stem:
            reporter.error(
                agent_path, "frontmatter name must match agent filename stem"
            )


def listed_skills(readme_path: Path) -> set[str]:
    """Return skill names listed in the plugin README inventory section."""
    text = readme_path.read_text(encoding="utf-8")
    section = extract_section(text, "## Included Skills")
    if not section:
        section = extract_section(text, "## Included Skill")
    names = set()
    names.update(table_first_column_names(section))
    for line in section.splitlines():
        bullet = re.match(r"^\s*-\s+`([a-z0-9]+(?:-[a-z0-9]+)*)`:", line)
        if bullet:
            names.add(bullet.group(1))
    return names


def validate_readme(plugin_root: Path, reporter: Reporter) -> None:
    """Validate README presence and skill inventory drift."""
    readme_path = plugin_root / "README.md"
    if not readme_path.is_file():
        reporter.error(plugin_root, "plugin README.md is required")
        return
    actual_skills = set(skill_names(plugin_root))
    readme_skills = listed_skills(readme_path)
    if actual_skills and not readme_skills:
        reporter.error(readme_path, "Included Skills inventory is required")
        return
    if actual_skills and readme_skills != actual_skills:
        missing = ", ".join(sorted(actual_skills - readme_skills)) or "none"
        extra = ", ".join(sorted(readme_skills - actual_skills)) or "none"
        reporter.error(
            readme_path,
            f"Included Skills inventory drift; missing: {missing}; extra: {extra}",
        )


def validate_plugin_agent_rules(plugin_root: Path, reporter: Reporter) -> None:
    """Validate plugin-local AGENTS/CLAUDE pointer files."""
    agent_rules = plugin_root / "AGENTS.md"
    claude_rules = plugin_root / "CLAUDE.md"
    if agent_rules.is_symlink():
        reporter.error(agent_rules, "plugin AGENTS.md must be a regular file")
    if not agent_rules.exists():
        return
    if not claude_rules.is_file():
        reporter.error(claude_rules, "plugin CLAUDE.md pointer is required")
        return
    if claude_rules.read_text(encoding="utf-8") != CLAUDE_AGENT_POINTER:
        reporter.error(claude_rules, "plugin CLAUDE.md must point to AGENTS.md")


def validate_marketplace(
    root: Path, plugin_roots: list[Path], reporter: Reporter
) -> None:
    """Validate root marketplace entries against manifested plugin roots."""
    marketplace_path = root / ".claude-plugin" / "marketplace.json"
    marketplace = read_json(marketplace_path, reporter)
    if not marketplace:
        return
    if marketplace.get("$schema") != MARKETPLACE_SCHEMA:
        reporter.error(marketplace_path, f"$schema must be {MARKETPLACE_SCHEMA}")
    entries = marketplace.get("plugins")
    if not isinstance(entries, list):
        reporter.error(marketplace_path, "plugins must be an array")
        return
    seen_sources: set[Path] = set()
    for entry in entries:
        if not isinstance(entry, dict):
            reporter.error(marketplace_path, "each plugin entry must be an object")
            continue
        validate_marketplace_entry(
            root, marketplace_path, entry, seen_sources, reporter
        )


def validate_marketplace_entry(
    root: Path,
    marketplace_path: Path,
    entry: dict[str, JsonValue],
    seen_sources: set[Path],
    reporter: Reporter,
) -> None:
    """Validate one marketplace plugin entry."""
    name = entry.get("name")
    source = entry.get("source")
    if not isinstance(name, str) or not isinstance(source, str):
        reporter.error(
            marketplace_path, "plugin entries must include string name and source"
        )
        return
    plugin_root = root / source.removeprefix("./")
    if not source.startswith("./plugins/") or not plugin_root.is_dir():
        reporter.error(marketplace_path, f"plugin source does not exist: {source}")
        return
    plugins_root = (root / "plugins").resolve()
    plugin_root_real = plugin_root.resolve()
    if not plugin_root_real.is_relative_to(plugins_root):
        reporter.error(
            marketplace_path,
            f"plugin source escapes plugins directory: {source}",
        )
        return
    if plugin_root_real in seen_sources:
        reporter.error(marketplace_path, f"duplicate plugin source: {source}")
        return
    seen_sources.add(plugin_root_real)
    if name != plugin_root.name:
        reporter.error(
            marketplace_path,
            f"plugin name {name} does not match source basename {plugin_root.name}",
        )
    if not (plugin_root / ".claude-plugin" / "plugin.json").is_file():
        reporter.error(marketplace_path, f"plugin source lacks manifest: {source}")


def validate(root: Path) -> list[str]:
    """Run all package validation checks."""
    reporter = Reporter(root)
    validate_root_link_layout(root, reporter)
    plugin_roots = sorted(
        path
        for path in (root / "plugins").iterdir()
        if path.is_dir() and (path / ".claude-plugin" / "plugin.json").is_file()
    )
    validate_marketplace(root, plugin_roots, reporter)
    for plugin_root in plugin_roots:
        validate_manifest(plugin_root, reporter)
        validate_skills(plugin_root, reporter)
        validate_agents(plugin_root, reporter)
        validate_readme(plugin_root, reporter)
        validate_plugin_agent_rules(plugin_root, reporter)
    return reporter.errors
