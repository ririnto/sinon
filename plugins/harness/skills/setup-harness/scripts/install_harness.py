#!/usr/bin/env -S uv run
# /// script
# dependencies = []
# ///

"""Install the setup-harness scaffold into a target repository."""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

SKILL_ROOT = Path(__file__).resolve().parents[1]
ASSETS_ROOT = SKILL_ROOT / "assets"
DOCS_ROOT = ASSETS_ROOT / "templates"

FILE_MAP = {
    "assets/config.json": "docs/harness/config.json",
    "assets/setup-hooks.sh": "scripts/harness/setup-hooks.sh",
    "scripts/install_harness.py": "scripts/harness/install_harness.py",
    "scripts/validate_harness.py": "scripts/harness/validate_harness.py",
    "scripts/validate_harness.sh": "scripts/harness/validate_harness.sh",
}

CI_FILE_MAP = {
    "github-actions": ("assets/github-actions.yml", ".github/workflows/harness-checks.yml"),
    "gitlab-ci": ("assets/gitlab-ci.yml", ".gitlab-ci.yml"),
}

DIRECTORY_MAP = (
    "docs/harness",
    "docs/exec-plans/active",
    "docs/exec-plans/completed",
)


AGENT_NAMES = (
    "agent-pool-curator",
    "architecture-guard",
    "ci-hooks-integrator",
    "code-reviewer",
    "config-curator",
    "doc-gardener",
    "e2e-driver",
    "implementation-drift-auditor",
    "quality-ratchet",
    "spec-writer",
)


class UnsafeDestinationError(ValueError):
    """Raised when an install destination can escape or follow symlinks."""


def normalize_relative_path(relative: str | Path) -> Path:
    """Return a safe relative destination path or raise for unsafe input."""
    relative_path = Path(relative)
    if (
        not relative_path.parts
        or relative_path.is_absolute()
        or ".." in relative_path.parts
    ):
        raise UnsafeDestinationError(f"unsafe destination path: {relative}")
    return relative_path


def reject_symlink_components(target_root: Path, relative_path: Path) -> None:
    """Reject symlinked parents and symlink file targets before writes."""
    current = target_root
    for part in relative_path.parts[:-1]:
        current = current / part
        if current.is_symlink():
            raise UnsafeDestinationError(f"refusing symlink directory component: {current}")
        if current.exists() and not current.is_dir():
            raise UnsafeDestinationError(f"destination parent is not a directory: {current}")
    target = target_root / relative_path
    if target.is_symlink():
        raise UnsafeDestinationError(f"refusing symlink destination: {target}")
    try:
        target.resolve(strict=False).relative_to(target_root)
    except ValueError as error:
        raise UnsafeDestinationError(f"destination escapes target root: {target}") from error


def prepare_file_destination(target_root: Path, relative: str | Path) -> Path:
    """Return a checked target path for file copy or overwrite."""
    relative_path = normalize_relative_path(relative)
    reject_symlink_components(target_root, relative_path)
    return target_root / relative_path


def prepare_directory_destination(target_root: Path, relative: str | Path) -> Path:
    """Return a checked target path for directory creation."""
    relative_path = normalize_relative_path(relative)
    reject_symlink_components(target_root, relative_path / ".keep")
    target = target_root / relative_path
    if target.is_symlink():
        raise UnsafeDestinationError(f"refusing symlink directory destination: {target}")
    return target


def parse_args() -> argparse.Namespace:
    """Parse installer arguments."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", type=Path, default=Path.cwd(), help="Target repository root.")
    parser.add_argument("--apply", action="store_true", help="Write files. Without this flag, run a dry run.")
    parser.add_argument("--overwrite", action="store_true", help="Overwrite existing files.")
    parser.add_argument("--agents", action="store_true", help="Install target-owned agent files into .claude/agents.")
    return parser.parse_args()


def copy_file(source: Path, target_root: Path, relative: str | Path, apply: bool, overwrite: bool) -> str:
    """Copy one file or report the dry-run action."""
    try:
        target = prepare_file_destination(target_root, relative)
    except UnsafeDestinationError as error:
        return f"unsafe {error}"
    if target.is_dir():
        return f"unsafe destination is a directory: {target}"
    if target.exists() and not overwrite:
        return f"conflict {target}"
    if not apply:
        return f"create {target}" if not target.exists() else f"overwrite {target}"
    existed = target.exists()
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
    return f"written {target}" if existed else f"created {target}"


def install_agent_context_alias(target_root: Path, apply: bool, overwrite: bool) -> str:
    """Install the AGENTS.md compatibility symlink when safe to do so."""
    try:
        target = prepare_file_destination(target_root, "AGENTS.md")
    except UnsafeDestinationError as error:
        return f"unsafe {error}"
    if target.exists() or target.is_symlink():
        if not overwrite:
            return f"conflict {target}"
        if not apply:
            return f"overwrite {target}"
        if target.is_dir():
            return f"unsafe destination is a directory: {target}"
        target.unlink()
    if not apply:
        return f"symlink {target} -> CLAUDE.md"
    target.symlink_to("CLAUDE.md")
    return f"symlinked {target} -> CLAUDE.md"


def install_docs(target_root: Path, apply: bool, overwrite: bool) -> list[str]:
    """Install copy-ready documentation files and empty directories."""
    actions: list[str] = []
    for relative in DIRECTORY_MAP:
        try:
            target = prepare_directory_destination(target_root, relative)
        except UnsafeDestinationError as error:
            actions.append(f"unsafe {error}")
            continue
        if not apply:
            actions.append(f"create {target}")
            continue
        target.mkdir(parents=True, exist_ok=True)
        actions.append(f"created {target}")
    for source in sorted(path for path in DOCS_ROOT.rglob("*") if path.is_file()):
        relative = source.relative_to(DOCS_ROOT)
        actions.append(copy_file(source, target_root, relative, apply, overwrite))
    return actions


def install_files(target_root: Path, apply: bool, overwrite: bool) -> list[str]:
    """Install core harness config, scripts, and CI files."""
    actions: list[str] = []
    for source_rel, target_rel in FILE_MAP.items():
        actions.append(copy_file(SKILL_ROOT / source_rel, target_root, target_rel, apply, overwrite))
    return actions


def configured_ci_provider(target_root: Path) -> str:
    """Return the configured CI provider from target config or bundled default."""
    for config_path in (target_root / "docs/harness/config.json", ASSETS_ROOT / "config.json"):
        if not config_path.is_file():
            continue
        config = json.loads(config_path.read_text())
        gates = config.get("gates")
        if isinstance(gates, dict):
            ci = gates.get("ci")
            if isinstance(ci, dict):
                provider = ci.get("provider")
                if isinstance(provider, str):
                    return provider
    return "none"


def install_ci(target_root: Path, apply: bool, overwrite: bool) -> list[str]:
    """Install only the configured CI provider surface."""
    provider = configured_ci_provider(target_root)
    if provider in ("none", "disabled", ""):
        return [f"skip CI templates (provider: {provider or 'none'})"]
    mapping = CI_FILE_MAP.get(provider)
    if mapping is None:
        return [f"unsafe unsupported CI provider: {provider}"]
    source_rel, target_rel = mapping
    return [copy_file(SKILL_ROOT / source_rel, target_root, target_rel, apply, overwrite)]


def install_agents(target_root: Path, root_name: str, apply: bool, overwrite: bool) -> list[str]:
    """Install target-owned agent files into the requested agent root."""
    actions: list[str] = []
    for name in AGENT_NAMES:
        source = ASSETS_ROOT / "agents" / f"{name}.md"
        actions.append(copy_file(source, target_root, f"{root_name}/{name}.md", apply, overwrite))
    return actions


def main() -> int:
    """Run the installer."""
    args = parse_args()
    target_root = args.target.resolve()
    actions = install_files(target_root, args.apply, args.overwrite)
    actions.extend(install_docs(target_root, args.apply, args.overwrite))
    actions.append(install_agent_context_alias(target_root, args.apply, args.overwrite))
    actions.extend(install_ci(target_root, args.apply, args.overwrite))
    if args.agents:
        actions.extend(install_agents(target_root, ".claude/agents", args.apply, args.overwrite))
    for action in actions:
        print(action)
    if not args.apply:
        print("dry run only; rerun with --apply to write files")
    return 1 if any(action.startswith(("conflict ", "unsafe ")) for action in actions) else 0


if __name__ == "__main__":
    raise SystemExit(main())
