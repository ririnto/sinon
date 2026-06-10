from __future__ import annotations

import os
from pathlib import Path

from .commands import workflow_name_for_mode
from .models import (
    AGENTS_MARKER,
    CLAUDE_MARKER,
    TEMPLATE_DIR,
    InstallCandidate,
    InstallPlan,
    InstallerSupport,
)
from .paths import is_common_skip_path, is_direct_template_entry


class PlanningMixin(InstallerSupport):
    def build_plan(self) -> InstallPlan:

        candidates = (
            self.common_install_candidates()
            + self.root_contract_install_candidates()
            + self.stack_install_candidates()
            + self.gitkeep_install_candidates()
        )
        return InstallPlan(tuple(candidates))

    def common_install_candidates(self) -> list[InstallCandidate]:

        src_dir = TEMPLATE_DIR / "common"
        candidates: list[InstallCandidate] = []
        if not src_dir.is_dir():
            return candidates
        for src in self.list_tracked_tree_files(src_dir):
            rel = src.relative_to(src_dir).as_posix()
            if is_common_skip_path(rel):
                continue
            candidates.append(
                InstallCandidate(
                    "seed" if is_direct_template_entry(rel) else "file",
                    rel,
                    src=src,
                    seed=is_direct_template_entry(rel),
                )
            )
        return candidates

    def stack_install_candidates(self) -> list[InstallCandidate]:

        src_dir = TEMPLATE_DIR / self.config.mode
        candidates: list[InstallCandidate] = []
        if not src_dir.is_dir():
            return candidates
        for src in self.list_tracked_tree_files(src_dir):
            rel = src.relative_to(src_dir).as_posix()
            if rel == ".gitlab-ci.yml":
                if self.config.ci_host in {"gitlab", "both"}:
                    candidates.append(
                        InstallCandidate("stack-file", ".gitlab-ci.yml", src=src)
                    )
                continue
            if rel.startswith(".github/workflows/"):
                if self.config.ci_host in {"github", "both"}:
                    workflow = workflow_name_for_mode(self.config.mode)
                    candidates.append(
                        InstallCandidate(
                            "stack-file", f".github/workflows/{workflow}", src=src
                        )
                    )
                continue
            candidates.append(InstallCandidate("stack-file", rel, src=src))
        return candidates

    def root_contract_install_candidates(
        self,
    ) -> list[InstallCandidate]:

        candidates = [
            self.root_contract_candidate_for_request(
                "AGENTS.md",
                AGENTS_MARKER,
                TEMPLATE_DIR / "common" / "AGENTS.md",
            ),
            self.root_contract_candidate_for_request(
                "CLAUDE.md",
                CLAUDE_MARKER,
                TEMPLATE_DIR / "common" / "CLAUDE.md",
            ),
        ]
        agents_exists = os.path.lexists("AGENTS.md")
        claude_exists = os.path.lexists("CLAUDE.md")
        agents_is_symlink = Path("AGENTS.md").is_symlink() if agents_exists else False
        claude_is_symlink = Path("CLAUDE.md").is_symlink() if claude_exists else False
        if not agents_exists and not claude_exists:
            candidates.append(
                InstallCandidate("symlink", "AGENTS.md", symlink_target="CLAUDE.md")
            )
        elif not agents_exists and claude_exists and not claude_is_symlink:
            candidates.append(
                InstallCandidate("symlink", "AGENTS.md", symlink_target="CLAUDE.md")
            )
        elif not claude_exists and agents_exists and not agents_is_symlink:
            candidates.append(
                InstallCandidate("symlink", "CLAUDE.md", symlink_target="AGENTS.md")
            )
        if Path(".claude").is_dir():
            candidates.append(
                InstallCandidate("symlink", ".agents", symlink_target=".claude")
            )
        return candidates

    def root_contract_candidate_for_request(
        self,
        request_file: str,
        marker: str,
        template_path: Path,
    ) -> InstallCandidate:

        if Path(request_file).is_symlink():
            real_target = self.root_contract_symlink_target(request_file)
            return InstallCandidate(
                "root-contract",
                request_file,
                src=template_path,
                real_target=real_target,
                marker=marker,
            )
        if Path(request_file).exists():
            return InstallCandidate(
                "root-contract",
                request_file,
                src=template_path,
                real_target=request_file,
                marker=marker,
            )
        other_file = "CLAUDE.md" if request_file == "AGENTS.md" else "AGENTS.md"
        if Path(other_file).is_symlink():
            real_target = self.root_contract_symlink_target(other_file)
            return InstallCandidate(
                "root-contract",
                request_file,
                src=template_path,
                real_target=real_target,
                marker=marker,
            )
        if Path(other_file).exists():
            return InstallCandidate(
                "root-contract",
                request_file,
                src=template_path,
                real_target=other_file,
                marker=marker,
            )
        return InstallCandidate(
            "root-contract",
            request_file,
            src=template_path,
            real_target="CLAUDE.md",
            marker=marker,
        )

    def gitkeep_install_candidates(self) -> list[InstallCandidate]:

        return [
            InstallCandidate("gitkeep", "docs/exec-plans/active/.gitkeep"),
            InstallCandidate("gitkeep", "docs/exec-plans/completed/.gitkeep"),
            InstallCandidate("gitkeep", "docs/generated/.gitkeep"),
        ]
