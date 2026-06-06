from __future__ import annotations

import os

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
        self.print_summary()
        self.runtime_advisory_for_mode()
