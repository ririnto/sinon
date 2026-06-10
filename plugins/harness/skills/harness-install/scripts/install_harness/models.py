from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

MODES = ("gradle", "maven", "uv", "bun", "shell")
CI_HOSTS = ("github", "gitlab", "both", "none")
VALIDATION_PLACEHOLDER = "{{validation_command}}"
AGENTS_MARKER = "# Repository Harness Contract"
CLAUDE_MARKER = "# Entry Point"

SCRIPT_PATH = Path(__file__).resolve().parent.parent / "install-harness.py"
SCRIPT_DIR = SCRIPT_PATH.parent
SKILL_DIR = SCRIPT_DIR.parent
TEMPLATE_DIR = SKILL_DIR / "assets"


@dataclass(frozen=True)
class InstallerConfig:
    """Configuration selected for one installer run.

    The fields mirror CLI flags after aliases and defaults are resolved.
    """

    mode: str
    ci_host: str
    target_root: Path
    force: bool
    action: str
    selected_path: str | None

    @property
    def validation_command(self) -> str:

        from .commands import validation_command_for_mode

        return validation_command_for_mode(self.mode)


@dataclass(frozen=True)
class InstallCandidate:
    """One target path selected for preview, rendering, or installation.

    Optional fields are populated only for candidate kinds that need them.
    """

    kind: str
    dst: str
    src: Path | None = None
    seed: bool = False
    real_target: str | None = None
    marker: str = ""
    symlink_target: str = ""


@dataclass(frozen=True)
class InstallPlan:
    """Resolved install candidates for the selected mode and CI host.

    The plan is reused by preview, show, single-file, and full installs.
    """

    candidates: tuple[InstallCandidate, ...]

    def match(self, target_path: str) -> InstallCandidate:

        from .errors import fail

        for candidate in self.candidates:
            if candidate.dst == target_path:
                return candidate
        fail(f"requested path is not in the selected install set: {target_path}")


class InstallerSupport:
    """Typed support surface shared by split installer mixins.

    The concrete HarnessInstaller supplies these members through composed mixins.
    """

    config: InstallerConfig

    def __init__(self, config: InstallerConfig) -> None:
        self.config = config

    def build_plan(self) -> InstallPlan:
        raise NotImplementedError

    def install_one_gitkeep_path(self, keep: str, *, create_only: bool = False) -> None:
        del keep, create_only
        raise NotImplementedError

    def ensure_one_root_contract(self, candidate: InstallCandidate) -> None:
        del candidate
        raise NotImplementedError

    def ensure_root_contracts(self) -> None:
        raise NotImplementedError

    def ensure_agents_symlink(self) -> None:
        raise NotImplementedError

    def ensure_gitkeep_paths(self) -> None:
        raise NotImplementedError

    def list_tracked_tree_files(self, src_dir: Path) -> list[Path]:
        del src_dir
        raise NotImplementedError

    def temporary_destination(self, dst: str, label: str) -> Path:
        del dst, label
        raise NotImplementedError

    def render_template(self, template_file: Path) -> str:
        del template_file
        raise NotImplementedError

    def root_contract_symlink_target(self, file_path: str) -> str:
        del file_path
        raise NotImplementedError

