from __future__ import annotations

import os
from pathlib import Path
import stat

from .commands import workflow_asset_name_for_ci_host, workflow_name_for_mode
from .errors import fail
from .models import TEMPLATE_DIR, InstallerSupport
from .paths import (
    ensure_safe_file_destination,
    is_common_skip_path,
    is_direct_template_entry,
    required_src,
)


class OperationsMixin(InstallerSupport):
    def install_one_target_path(self, requested_path: str) -> None:

        candidate = self.build_plan().match(requested_path)
        if candidate.kind in {"file", "stack-file", "seed"}:
            self.copy_asset_file(
                required_src(candidate),
                candidate.dst,
                seed=candidate.seed,
            )
            return
        if candidate.kind == "gitkeep":
            self.install_one_gitkeep_path(candidate.dst)
            return
        if candidate.kind == "root-contract":
            self.ensure_one_root_contract(candidate)
            return
        if candidate.kind == "symlink":
            fail(
                f"cannot place symlink entry with --only; run full install for {requested_path}"
            )
        fail(f"unsupported --only target selection: {requested_path}")

    def install_full_plan(self) -> None:

        self.ensure_root_contracts()
        self.copy_tree(TEMPLATE_DIR / "common", ".", common=True)
        self.ensure_runtime_symlinks()
        self.ensure_gitkeep_paths()
        self.copy_stack_tree(TEMPLATE_DIR / self.config.mode, ".")

    def copy_tree(self, src_dir: Path, dst_dir: str, *, common: bool) -> None:

        if not src_dir.is_dir():
            return
        for src in self.list_tracked_tree_files(src_dir):
            rel = src.relative_to(src_dir).as_posix()
            if common and is_common_skip_path(rel):
                continue
            if common and rel == "WORKFLOW.md":
                src = src_dir / workflow_asset_name_for_ci_host(self.config.ci_host)
            dst = rel if dst_dir in {"", "."} else f"{dst_dir}/{rel}"
            self.copy_asset_file(
                src,
                dst,
                seed=common and is_direct_template_entry(rel),
            )

    def copy_stack_tree(self, src_dir: Path, dst_dir: str) -> None:

        if not src_dir.is_dir():
            return
        workflow_name = workflow_name_for_mode(self.config.mode)
        for src in self.list_tracked_tree_files(src_dir):
            rel = src.relative_to(src_dir).as_posix()
            if rel == ".gitlab-ci.yml":
                if self.config.ci_host not in {"gitlab", "both"}:
                    continue
                dst = rel if dst_dir in {"", "."} else f"{dst_dir}/{rel}"
                self.copy_asset_file(src, dst)
                continue
            if rel.startswith(".github/workflows/"):
                if self.config.ci_host not in {"github", "both"}:
                    continue
                dst = (
                    f".github/workflows/{workflow_name}"
                    if dst_dir in {"", "."}
                    else f"{dst_dir}/.github/workflows/{workflow_name}"
                )
                self.copy_asset_file(src, dst)
                continue
            dst = rel if dst_dir in {"", "."} else f"{dst_dir}/{rel}"
            self.copy_asset_file(src, dst)

    def copy_asset_file(self, src_file: Path, dst: str, *, seed: bool = False) -> None:

        ensure_safe_file_destination(dst)
        if Path(dst).exists() and not self.config.force:
            if seed:
                print(f"skip seed (target exists): {dst}")
            else:
                print(f"keep existing: {dst}")
            return
        tmp = self.temporary_destination(dst, "copy_file")
        tmp.write_text(self.read_install_asset(src_file), encoding="utf-8")
        if os.access(src_file, os.X_OK):
            file_mode = tmp.stat().st_mode
            tmp.chmod(file_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
        had_existing = Path(dst).exists()
        os.replace(tmp, dst)
        if had_existing:
            if seed:
                print(f"overwrite seed (--force): {dst}")
            else:
                print(f"overwrite (--force): {dst}")
        else:
            if seed:
                print(f"deliver seed: {dst}")
            else:
                print(f"write: {dst}")
