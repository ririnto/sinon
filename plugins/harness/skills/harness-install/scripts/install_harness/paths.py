from __future__ import annotations

from pathlib import Path

from .errors import fail
from .models import InstallCandidate


def normalize_requested_target_path(requested_path: str) -> str:

    normalized = (
        requested_path[2:] if requested_path.startswith("./") else requested_path
    )
    reject_unsafe_relative_path(normalized)
    if normalized.endswith("/"):
        fail(f"unsafe target path: {normalized} (must be a file path, not a directory)")
    return normalized


def reject_unsafe_relative_path(path: str) -> None:

    unsafe_path = path[2:] if path.startswith("./") else path
    if unsafe_path in {"", "."} or unsafe_path.startswith("/"):
        fail(
            f"unsafe target path: {path} (must be relative, non-empty, no .. references)"
        )
    parts = unsafe_path.split("/")
    if any(part in {"", ".", ".."} for part in parts):
        fail(
            f"unsafe target path: {path} (must be relative, non-empty, no .. references)"
        )


def ensure_safe_parent_dir(path: str) -> None:

    parent = Path(path).parent
    if str(parent) in {"", "."}:
        return
    reject_unsafe_relative_path(parent.as_posix())
    current = Path()
    for part in parent.parts:
        current = current / part
        if current.is_symlink():
            fail(
                f"[safe_parent] refusing symlink directory component: {current.as_posix()}"
            )
        if current.exists() and not current.is_dir():
            fail(
                f"[safe_parent] parent component is not a directory: {current.as_posix()}"
            )
    parent.mkdir(parents=True, exist_ok=True)


def ensure_safe_file_destination(path: str) -> None:

    clean_path = path[2:] if path.startswith("./") else path
    reject_unsafe_relative_path(clean_path)
    ensure_safe_parent_dir(clean_path)
    target = Path(clean_path)
    if target.is_symlink():
        fail(f"[safe_destination] refusing symlink file destination: {clean_path}")
    if target.is_dir():
        fail(f"[safe_destination] refusing directory file destination: {clean_path}")


def is_common_skip_path(rel: str) -> bool:

    return rel in {
        "AGENTS.md",
        "CLAUDE.md",
        "docs/harness/git-hooks/pre-commit",
        "docs/harness/git-hooks/pre-push",
    }


def is_direct_template_entry(rel: str) -> bool:

    return rel.startswith("docs/harness/templates/") and rel.count("/") == 3


def required_selected_path(config) -> str:

    if config.selected_path is None:
        fail(f"--{config.action} requires a path argument.")
    return config.selected_path


def required_src(candidate: InstallCandidate) -> Path:

    if candidate.src is None:
        fail(f"candidate has no source: {candidate.dst}")
    return candidate.src


def required_real_target(candidate: InstallCandidate) -> str:

    if candidate.real_target is None:
        fail(f"candidate has no root contract target: {candidate.dst}")
    return candidate.real_target
