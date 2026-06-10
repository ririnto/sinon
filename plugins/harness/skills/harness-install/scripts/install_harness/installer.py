from __future__ import annotations

import os
import subprocess

from .advisory import AdvisoryMixin
from .contracts import ContractsMixin
from .hooks import HooksMixin
from .models import InstallerConfig
from .operations import OperationsMixin
from .paths import normalize_requested_target_path, required_selected_path
from .planning import PlanningMixin
from .preview import PreviewMixin


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
            result = subprocess.run(
                ["git", "config", "core.hooksPath", ".githooks/"],
                capture_output=True,
                text=True,
            )
            if result.returncode == 0:
                print("activate git hooks: git config core.hooksPath .githooks/")
            else:
                print(
                    f"[warning] git config core.hooksPath failed: {result.stderr.strip()}",
                )
        elif mode == "uv":
            result = subprocess.run(
                ["uv", "run", "pre-commit", "install"],
                capture_output=True,
                text=True,
            )
            if result.returncode == 0:
                print("activate git hooks: uv run pre-commit install")
            else:
                print(
                    f"[warning] pre-commit install failed: {result.stderr.strip()}",
                )
        elif mode == "bun":
            result = subprocess.run(
                ["bun", "install"],
                capture_output=True,
                text=True,
            )
            if result.returncode == 0:
                print("activate git hooks: bun install (Husky prepare)")
            else:
                print(
                    f"[warning] bun install failed: {result.stderr.strip()}",
                )
