---
title: Harness
description: >-
  Overview of the Harness plugin, its install, validation, evolution, templates, agents, skills, CI, and Git hook scaffolding workflow.
---

# Harness

Harness is a Claude Code plugin for installing, validating, and evolving repository-owned agent development scaffolding. It combines the v6 archive structure with this repository's plugin packaging rules: skills are the declared runtime surface, root agents are host-dependent and undeclared in the manifest, and target repositories own the files copied into them.

## Purpose

- Install a repository harness with `AGENTS.md`, `CLAUDE.md`, `ARCHITECTURE.md`, a structured `docs/` tree, `.claude/` project agents, `.claude/` project skills, templates, validation adapters, CI snippets, and opt-in Git hook templates.
- Keep implementation, repository docs, product specs, execution plans, generated references, and deterministic checks evolving together so agents work from versioned context rather than hidden convention.
- Preserve harness-only development readiness: the scaffold gives agents a working operating surface, but target repositories still supply product requirements, architecture decisions, source code, runtime configuration, secrets, and domain references.
- Provide workflow templates with bounded concurrency, handoff states, workspace policies, prompt contracts, and proof-of-work expectations so agents can participate in ticket-level orchestration without requiring a daemon or issue poller.

## Lifecycle

1. Install repository-owned harness files into a target repository.
2. Validate the installed context with the target repository's matching stack command.
3. Evolve templates, docs, agents, skills, generated-artifact policy, and validation rules as project reality changes.

## Install in Claude Code

Install from this repository checkout:

```sh
claude plugin install ./plugins/harness --scope project
```

## Included Skills

- `harness-install`: install repository-owned harness assets into a target repository.
- `harness-validate`: validate installed harness assets and stack adapters against the harness contract.
- `harness-evolve`: update the harness contract after repeated project use exposes new template, validation, or workflow needs.

## Included Agents

- `harness-architect`: plan harness structure and installation strategy.
- `harness-reviewer`: review installed harness assets and evolution proposals.
- `harness-validator`: diagnose validation output and readiness gates.

## Packaged Scripts and Templates

- `scripts/plugin-self-check.sh` validates packaged and tracked plugin files.
- `skills/harness-install/templates/` contains files the installer copies into target repositories, including `.claude/agents`, `.claude/skills`, `.claude/harness`, docs, CI, validation adapters, and Git hook scaffolds.

## Runtime Model

The Claude Code manifest declares only `./skills/`. Agents remain in the `agents/` directory at the plugin root and are described here rather than declared in `.claude-plugin/plugin.json` because this repository's manifest rules prohibit an `agents` key. Agents are available to host runtimes that load plugin agents from the plugin root; hosts that do not load plugin-root agents still receive the skill surface. The plugin does not expose top-level hooks; packaged hook scaffolds live under `skills/harness-install/templates/common/.claude/harness/git-hooks/`, and the installer copies the scaffold sources before rendering selected-mode pre-commit and pre-push hook templates.

## Target Ownership

Target repositories own every installed harness file. Copied docs, scripts, CI files, hooks, agents, skills, templates, and validation adapters MAY be edited, renamed, or removed to fit the target project. The installed `.claude/harness/manifest.json` is the target repository harness metadata contract; it replaces the old `docs/harness/config.json` convention used by earlier drafts of this plugin.

## Install Harness Assets

From a target repository, ask Claude Code to use the `harness-install` skill with stack mode `auto` or an explicit mode such as `gradle`, `maven`, `uv`, or `bun`.

The skill invokes `skills/harness-install/scripts/install-harness.sh` with the target repository as `--target`, detects or accepts the stack mode, copies repository-level files and `.claude/` assets, then prints the stack-specific validation command. Supported modes are `auto`, `gradle`, `maven`, `uv`, and `bun`.

By default the installer writes both GitHub Actions and GitLab CI examples for the selected stack: `.github/workflows/harness.yml` and `.gitlab-ci.yml`. Both CI snippets are rendered from templates and run the same final check command as generated `pre-push`; for Gradle this is `check`, not `harnessValidate`. Pass `--no-ci` to skip both CI files.

## CI Host Selection

The installer ships both CI examples so it never has to guess where the target publishes builds. Pick the active host as a post-install step:

- GitHub-only repositories SHOULD delete `.gitlab-ci.yml` after install and keep `.github/workflows/harness.yml`.
- GitLab-only repositories SHOULD delete `.github/workflows/harness.yml` after install and keep `.gitlab-ci.yml`.
- Repositories that mirror to both hosts MAY keep both files; the generated `pre-push` final check command must match both CI scripts so `harness-validate` does not report drift.
- Targets that do not run CI at all SHOULD pass `--no-ci` on install and document the policy in the project README.

`harness-validate` reports `.github/workflows/harness.yml` and `.gitlab-ci.yml` as missing rather than mismatched when they are intentionally absent for the selected host.

To run the installer directly, pass the target repository explicitly:

```sh
sh /path/to/sinon/plugins/harness/skills/harness-install/scripts/install-harness.sh --target /path/to/target-repo --mode auto --hooks none
```

## Required Repository Structure

The installer creates this repository context structure, and validators require it:

```text
AGENTS.md
ARCHITECTURE.md
CLAUDE.md
.claude/
├── agents/
│   ├── harness-implementation-agent.md
│   ├── harness-orchestrator.md
│   └── harness-review-agent.md
├── skills/
│   ├── harness-orchestrate/
│   │   └── SKILL.md
│   ├── harness-review/
│   │   └── SKILL.md
│   └── harness-validate/
│       └── SKILL.md
└── harness/
    ├── git-hooks/
    │   ├── pre-commit
    │   └── pre-push
    ├── manifest.json
    └── templates/
docs/
├── design-docs/
│   ├── index.md
│   └── core-beliefs.md
├── exec-plans/
│   ├── active/
│   │   └── .gitkeep
│   ├── completed/
│   │   └── .gitkeep
│   └── tech-debt-tracker.md
├── generated/
│   └── .gitkeep
├── product-specs/
│   ├── index.md
│   └── new-user-onboarding.md
├── references/
│   ├── design-system-reference-llms.txt
│   ├── nixpacks-llms.txt
│   └── uv-llms.txt
├── DESIGN.md
├── FRONTEND.md
├── PLANS.md
├── PRODUCT_SENSE.md
├── QUALITY_SCORE.md
├── RELIABILITY.md
└── SECURITY.md
```

Empty required directories are kept in version control with `.gitkeep`. `.claude/harness/git-hooks/pre-commit` is a generated, target-owned hook template: Gradle uses it for `harnessValidate`, while non-Gradle stacks use it for compliance checks. `.claude/harness/git-hooks/pre-push` is the generated final-check hook template: Gradle uses `check`, while non-Gradle stacks use the selected validation command. Neither is an active Git hook unless the target repository opts in. `docs/generated/` is a generated-artifact location, not a required database-documentation location. Generated artifacts SHOULD document their source command, source inputs, freshness, and regeneration trigger.

In installed target repositories, `AGENTS.md` is the primary harness contract. `CLAUDE.md` is retained as the Claude Code entry point and points back to `AGENTS.md`.

## Validation Adapters

| Stack | Detection | Validation command |
| --- | --- | --- |
| Gradle local harness validation | `settings.gradle(.kts)` or `build.gradle(.kts)` | `./gradlew harnessValidate`, or `gradle harnessValidate` when the target uses system Gradle without a wrapper |
| Gradle final check | `settings.gradle(.kts)` or `build.gradle(.kts)` | `./gradlew check`, or `gradle check` when the target uses system Gradle without a wrapper |
| Maven | `pom.xml` | `mvn -q -f .claude/harness/maven-plugin/pom.xml install && mvn -q ai.harness:harness-maven-plugin:0.1.0:validate` |
| uv | `uv.lock` or Python `pyproject.toml` | `uv run python .claude/harness/uv/harness_validate.py` |
| bun | `bun.lock`, `bun.lockb`, or `package.json` | `bun run .claude/harness/bun/harness-validate.ts` |

Run validation commands from the target repository root. The uv, bun, and Maven validators bind that current directory as the target root, and native validators compare the installed `.claude/harness/manifest.json` fields that this plugin writes.

Gradle installer wiring prepends a composite build include for `.claude/harness/gradle-plugin`. Complex existing `settings.gradle(.kts)` files, especially those with custom plugin management or composite builds, SHOULD be reviewed manually after installation.

## Git Hooks

The installer writes two selected-mode hook templates in `.claude/harness/git-hooks/` on fresh install. Gradle `pre-commit` runs `harnessValidate` for intermediate harness feedback, and Gradle `pre-push` runs `check` for the final push gate. Non-Gradle `pre-commit` performs lightweight harness-rule compliance checks, and non-Gradle `pre-push` runs the selected validation command. Managed generated templates refresh with the selected intermediate and final commands; custom target-owned templates are preserved unless `--force` is used. Git hook activation is opt-in because it modifies local Git behavior outside version control.

```sh
sh /path/to/sinon/plugins/harness/skills/harness-install/scripts/install-harness.sh --target /path/to/target-repo --mode auto --hooks copy
```

Use `--hooks none` or omit the flag to skip Git hook activation. Use `--hooks copy` only when the target should copy both generated hooks to `pre-commit` and `pre-push` in the active worktree hooks directory.

Copy mode keeps existing active local hooks unless `--force` is used. With `--force`, the installer replaces both active local hooks with installer-generated content for the selected mode. Use `--hooks copy --force` only with explicit approval because it changes local Git behavior.

### Worktree-aware hook installation

`--hooks copy` resolves the active hooks directory through `git rev-parse --git-common-dir`. In a primary worktree this is `.git/hooks/`. In a linked worktree created by `git worktree add`, `.git` is a file that points back to the main worktree's `.git/worktrees/<name>/` directory; the installer still installs into the shared `<main>/.git/hooks/` so the same hooks fire from every worktree of the same repository. The installer refuses to write through a `.git` symlink or when the worktree is not initialized.

### Recommended pattern: harness-tracked hooks via `core.hooksPath`

The harness ships `pre-commit` and `pre-push` under `.claude/harness/git-hooks/`, which is committed to the repository. Point Git at that directory to activate the hooks for the current clone without copying anything:

```sh
git config core.hooksPath .claude/harness/git-hooks
```

With this config, generated hook content evolves through normal version control: every worktree of the same clone sees the same hooks, and `harness-install` refreshes the active hooks in place when re-run with `--hooks none`. The installer refuses `--hooks copy` while `core.hooksPath` already resolves to `.claude/harness/git-hooks/` because copying to the worktree hooks directory would not activate the worktree hooks.

The harness itself never sets `core.hooksPath`; the target repository owner enables the recommended pattern explicitly. Unset it with `git config --unset core.hooksPath` to revert.

## Metadata and Attribution

- Manifest author: `ririnto`, matching the repository owner metadata.
- Plugin license: Apache-2.0, recorded in `LICENSE`.
- External inspiration and adapted taxonomy are documented in `THIRD_PARTY_NOTICES.md`.
- Marketplace releases are versioned at `.claude-plugin/marketplace.json`; plugin manifests intentionally omit `version`.

## Harness Evolution

The installed harness MAY evolve as the project moves through discovery, implementation, hardening, release, and maintenance. Treat the current committed harness files as the active target contract. Use `harness-evolve` when repeated validation or review failures show that templates, docs, agents, skills, generated-artifact locations, or validation rules should change.

## Layout

```text
plugins/harness/
├── .claude-plugin/plugin.json
├── LICENSE
├── README.md
├── THIRD_PARTY_NOTICES.md
├── agents/
├── scripts/
└── skills/
    ├── harness-evolve/
    ├── harness-install/
    └── harness-validate/
```

## Scope Notes

- This plugin prepares repository knowledge, guardrails, templates, and validation paths. It does not run background services or manage issue queues.
- Target-owned agents are starting points, not immutable plugin internals.
- The installed `.claude/skills/harness-validate/` directory is a project skill. Prefer that project skill when validating an installed target repository. Use the plugin-provided `harness-validate` skill when working from this plugin checkout or before the target repository has its project copy. If a host cannot distinguish the project skill from the plugin skill, rename the installed project directory to `.claude/skills/project-harness-validate/`, update its `SKILL.md` `name` field to `project-harness-validate`, and update local project docs to use that name.
- GitHub Actions and GitLab CI templates use ordinary version tags from the archive; projects with strict supply-chain policy SHOULD pin actions and images to reviewed immutable references after installation.
- Maven, Gradle, Python, and test self-checks may create cache, IDE, or build metadata under template directories. Repository `.gitignore` and installer filters exclude ignored byproducts such as `__pycache__/`, `.pytest_cache/`, `*.pyc`, `.classpath`, `.project`, `.factorypath`, `.settings/`, `.gradle/`, `bin/`, `build/`, and `target/`; plugin self-checks validate packaged/tracked template files rather than failing on ignored working-tree byproducts.
