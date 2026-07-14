---
description: >-
  Overview of the Harness plugin for installing and evolving repository harness assets.
---

# Harness

Harness installs repository scaffolding for agent-assisted development.
It packages target-owned files only under `skills/harness-install/assets/`.

## Setup

Register the marketplace, then install Harness:

```sh
claude plugin marketplace add /path/to/sinon
claude plugin install harness@sinon
```

Use an installed skill or run the installer from the plugin root. Choose one mode and one CI host explicitly:

```sh
"${CLAUDE_PLUGIN_ROOT:-/path/to/sinon/plugins/harness}/skills/harness-install/scripts/install-harness.ts" \
  --target /path/to/target-repo \
  --mode bun \
  --ci-host github
```

`--mode` MUST be one of `bun`, `gradle`, `maven`, `shell`, or `uv`.
`--ci-host` MUST be one of `github`, `gitlab`, `both`, or `none`.
Run `--preview` before installing into a repository that already contains target paths.

## Skills

| Skill | Use |
| --- | --- |
| `harness-install` | Install packaged target assets. |
| `harness-evolve` | Report how one target or future defaults should change. |

Installed targets also receive `autonomous-execution` and `issue-mining` under `.claude/skills/`.
They work from their own instructions; a target `WORKFLOW.md` may add local constraints.

## Package Inventory

- `.claude-plugin/plugin.json`: plugin metadata.
- `skills/harness-install/`: installer, asset manifest, packaged target assets, and stack adapters.
- `skills/harness-evolve/`: report-first evolution guidance.

The installer writes target contracts, docs, project skills, selected stack validation assets, optional CI assets, and inactive `.githooks/` templates.

Repository-side tests and development tools live outside this published plugin.

## Runtime and Ownership

The installer compares target bytes with packaged sources:

- Missing files are created.
- Matching files are kept.
- Differing seed and root-contract files are preserved.
- Other differing files report conflicts.

`--force` overwrites allowed conflicting content. Use it only with explicit approval.
`--only <path>` creates or updates one selected target path.

Each mode copies `pre-commit` and `pre-push` templates to `.githooks/` but leaves Git configuration unchanged. `--activate-hooks` is supported only for a full install and sets repository-local `core.hooksPath` after validating the copied hooks. Use it only with explicit approval.

`--ci-host` controls CI files only. Every selection installs the same target `WORKFLOW.md`.

## Validation

After installation, run the canonical validation command printed by the installer from the target root:

```sh
<printed canonical validation command>
```

CI and installed `pre-commit`, when present, MUST use that same command. `pre-push` may run a stricter local command.

## Security Boundary

The installer rejects lexical traversal and observed symlinks, uses exclusive same-directory temporary files, rechecks immediate parent identity, and rejects destination symlinks. It requires trusted target ancestry and does not provide full TOCTOU, hostile-ancestor, APFS-normalization, or hard-link containment.
