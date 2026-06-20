from __future__ import annotations

from dataclasses import dataclass
import os
import shutil
import subprocess

from .advisory import AdvisoryMixin
from .contracts import ContractsMixin
from .hooks import HooksMixin
from .models import InstallerConfig
from .operations import OperationsMixin
from .paths import normalize_requested_target_path, required_selected_path
from .planning import PlanningMixin
from .preview import PreviewMixin


@dataclass(frozen=True, slots=True)
class ActivationCommand:
    """Command used to activate the selected stack hooks."""

    executable: str
    command: tuple[str, ...]
    success_message: str
    failure_label: str


class HarnessInstaller(
    PlanningMixin,
    PreviewMixin,
    OperationsMixin,
    ContractsMixin,
    HooksMixin,
    AdvisoryMixin,
):
    def __init__(self, config: InstallerConfig) -> None:

        super().__init__(config)
        self.target_root = config.target_root.resolve()

    def run(self) -> None:

        from .errors import fail

        if not self.target_root.is_dir():
            fail(f"target root is not a directory: {self.config.target_root}")
        os.chdir(self.target_root)
        if self.config.action == "preview":
            self.preview_install_set()
            return
        if self.config.action == "show":
            selected_path = normalize_requested_target_path(
                required_selected_path(self.config)
            )
            self.show_one_target_path(selected_path)
            return
        if self.config.action == "only":
            selected_path = normalize_requested_target_path(
                required_selected_path(self.config)
            )
            self.install_one_target_path(selected_path)
            self.print_summary(selected_path)
            self.runtime_advisory_for_mode()
            return
        self.install_full_plan()
        self.activate_git_hooks()
        self.print_summary()
        self.runtime_advisory_for_mode()

    def activate_git_hooks(self) -> None:

        mode = self.config.mode
        if mode == "gradle":
            print(
                "activate git hooks: Gradle plugin creates hooks on first build",
            )
        elif mode in {"maven", "shell"}:
            self.run_activation_command(
                ActivationCommand(
                    executable="git",
                    command=("git", "config", "core.hooksPath", ".githooks/"),
                    success_message="activate git hooks: git config core.hooksPath .githooks/",
                    failure_label="git config core.hooksPath",
                )
            )
        elif mode == "uv":
            self.run_activation_command(
                ActivationCommand(
                    executable="uv",
                    command=("uv", "run", "pre-commit", "install"),
                    success_message="activate git hooks: uv run pre-commit install",
                    failure_label="pre-commit install",
                )
            )
        elif mode == "bun":
            self.run_activation_command(
                ActivationCommand(
                    executable="bun",
                    command=("bun", "install"),
                    success_message="activate git hooks: bun install (Husky prepare)",
                    failure_label="bun install",
                )
            )

    def run_activation_command(self, activation: ActivationCommand) -> None:
        """Run one hook activation command when its executable is installed."""

        if shutil.which(activation.executable) is None:
            print(
                f"[warning] {activation.executable} not in PATH; "
                f"skipping {activation.failure_label}"
            )
            return
        result = subprocess.run(
            activation.command,
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode == 0:
            print(activation.success_message)
            return
        print(f"[warning] {activation.failure_label} failed: {result.stderr.strip()}")
