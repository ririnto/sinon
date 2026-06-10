from __future__ import annotations

import os
from pathlib import Path
import sys

from .errors import fail, read_text
from .models import InstallCandidate, InstallerSupport
from .paths import required_real_target, required_src


class PreviewMixin(InstallerSupport):
    def preview_install_set(self) -> None:

        print(f"target: {Path.cwd()}")
        print(f"mode: {self.config.mode}")
        print(f"ci-host: {self.config.ci_host}")
        print(f"validation command: {self.config.validation_command}")
        for candidate in self.build_plan().candidates:
            self.preview_candidate_status(candidate)

    def preview_candidate_status(self, candidate: InstallCandidate) -> None:

        if candidate.kind in {"file", "seed", "stack-file"}:
            self.preview_file_candidate(candidate)
        elif candidate.kind == "root-contract":
            self.preview_root_contract_candidate(candidate)
        elif candidate.kind == "symlink":
            self.preview_symlink_candidate(candidate)
        elif candidate.kind == "gitkeep":
            self.preview_gitkeep_candidate(candidate)
        else:
            print(f"skip unknown candidate: {candidate.dst}", file=sys.stderr)

    def preview_file_candidate(self, candidate: InstallCandidate) -> None:

        if candidate.seed and Path(candidate.dst).exists():
            print(f"skip seed (target exists): {candidate.dst}")
            return
        if Path(candidate.dst).exists():
            if self.config.force:
                print(f"overwrite (--force): {candidate.dst}")
            else:
                print(f"keep existing: {candidate.dst}")
            return
        print(f"write: {candidate.dst}")

    def preview_root_contract_candidate(self, candidate: InstallCandidate) -> None:

        real_target = required_real_target(candidate)
        if Path(real_target).exists() and candidate.marker in read_text(real_target):
            print(f"skip root contract: {candidate.dst}")
            return
        if Path(real_target).exists() and not self.config.force:
            print(
                f"conflict root contract: {real_target} lacks marker {candidate.marker}; rerun with --force to append",
                file=sys.stderr,
            )
            return
        if Path(real_target).exists():
            print(f"update root contract (--force): {candidate.dst}")
        else:
            print(f"create root contract: {candidate.dst}")

    def preview_symlink_candidate(self, candidate: InstallCandidate) -> None:

        if Path(candidate.dst).is_symlink():
            current_target = os.readlink(candidate.dst)
            if current_target == candidate.symlink_target:
                print(
                    f"skip existing symlink: {candidate.dst} -> {candidate.symlink_target}"
                )
            else:
                print(
                    f"skip existing symlink: {candidate.dst} -> {current_target}",
                    file=sys.stderr,
                )
            return
        if Path(candidate.dst).exists():
            print(f"skip existing: {candidate.dst}")
            return
        print(f"create symlink: {candidate.dst} -> {candidate.symlink_target}")

    def preview_gitkeep_candidate(self, candidate: InstallCandidate) -> None:

        if Path(candidate.dst).exists():
            if self.config.force:
                print(f"overwrite (--force): {candidate.dst}")
            else:
                print(f"keep existing: {candidate.dst}")
            return
        print(f"write: {candidate.dst}")

    def show_one_target_path(self, requested_path: str) -> None:

        candidate = self.build_plan().match(requested_path)
        if candidate.kind in {"file", "seed", "stack-file"}:
            print(self.render_template(required_src(candidate)), end="")
            return
        if candidate.kind == "root-contract":
            real_target = required_real_target(candidate)
            if Path(real_target).exists() and candidate.marker in read_text(
                real_target
            ):
                return
            if Path(real_target).exists():
                print(
                    f"note: requested root contract content would be appended to existing file: {real_target}",
                    file=sys.stderr,
                )
            print(self.render_template(required_src(candidate)), end="")
            return
        if candidate.kind == "gitkeep":
            return
        if candidate.kind == "symlink":
            fail(
                f"--show on symlink entry is not supported; run full install to create {requested_path}"
            )
        fail(f"requested path is not renderable: {requested_path}")
