---
description: >-
  Overview of the Harness plugin, its install, validation, evolution, repository templates, agents, skills, CI, and Git hook scaffolding workflow.

---

# Harness

Harness is a Claude Code plugin for installing, validating, and evolving agent development scaffolding for repositories.
It packages the current harness assets inside the plugin layout required by this repository.
Skills are the declared runtime surface.
Plugin-root agents are structural harness specialists for changing a target repository's harness contract.
Files that should live inside a target repository are packaged only under `skills/harness-install/assets/`.

## Role

This README covers:

- Harness installation outputs
- packaged asset locations
- install, validate, and evolve flow

Installed assets under `assets/common` and `assets/<mode>` carry repository runtime details.

## Purpose

- Install a repository harness with `AGENTS.md`, `CLAUDE.md` pointer, `ARCHITECTURE.md`, docs, project agents and skills, repository templates, validators, CI files, and hook templates.
- Keep implementation, repository docs, product specs, execution plans, generated references, and deterministic checks evolving together.
  - Agents should work from versioned context rather than hidden convention.
- Preserve harness-only development readiness.
  - Target repositories still supply requirements, architecture decisions, source code, runtime configuration, secrets, and domain references.
- Provide `WORKFLOW.md` for branch policy, host CLI use, validation, review, and publication.

## Lifecycle

1. Install harness files into a target repository.
2. Validate the installed context with the target repository's matching stack command.
3. Evolve docs, repository templates, agents, skills, generated-artifact policy, and validation rules as project reality changes.

## Install in Claude Code

Register the `sinon` marketplace from a local checkout, then install the harness plugin from it:

```sh
claude plugin marketplace add /path/to/sinon
claude plugin install harness@sinon
```

## Included Skills

- `harness-install`: install harness assets into a target repository.
- `harness-validate`: validate installed harness assets and stack adapters against the harness contract.
- `harness-evolve`: update the harness contract after repeated project use exposes new template, validation, or workflow needs.

## Plugin-Owned Structural Agents

These agents are plugin-owned advisory surfaces for the harness lifecycle itself.
They are valid when the task is to design, review, or verify the target repository's harness structure.
They are not installed as day-to-day target repository agents.

- `harness-architect`: plan target harness structure, ownership boundaries, and validation alignment.
- `harness-reviewer`: review target harness assets and evolution proposals for contract drift.
- `harness-validator`: diagnose target harness validation output and readiness gates.

All three plugin-owned agents are substantive leaf specialists and declare Claude Sonnet with medium effort.
They do not delegate or publish.

## Packaged Scripts and Assets

- `scripts/plugin-self-check.ts` validates packaged files, stack assets, hook wiring, CI parity, package metadata, installer outcome scenarios, style hardening, and native-tool smoke checks.
  - It skips a stack gracefully when its toolchain is absent from PATH.
- `skills/harness-install/scripts/install-harness.ts` is the executable installer entry point.
  - `skills/harness-install/scripts/install-harness/` contains the packaged implementation modules.
  - The installer reads the checked-in `skills/harness-install/asset-manifest.json`, so an installed plugin cache does not require Git metadata.
- `skills/harness-validate/scripts/validate-install-record.ts` is a self-contained check for recorded outcomes, ownership, target drift, managed-block drift, ownership digests, command parity, and inventory integrity.
- `skills/harness-install/assets/` contains files the installer copies into target repositories, including `.claude/agents`, `.claude/skills`, `docs`, CI files, validation adapters, and Git hook scaffolds.
- Long Markdown files under `skills/harness-install/assets/common/docs/references/` are packaged reference material, not source modules.

## Runtime Model

The Claude Code manifest declares no component path fields.
Claude Code discovers plugin-root `skills/` and `agents/` by default.
Plugin-root agents remain in `agents/` as optional structural specialists for changing the target repository's harness contract.
They are not copied into target repositories.
Target repository agents, project skills, docs, CI files, validators, and hook scaffolds are packaged under `skills/harness-install/assets/` and become project files after installation.
The plugin does not expose top-level hooks.
Stack assets provide inactive POSIX `pre-commit` and `pre-push` templates for the selected mode.
The installer activates them only when `--activate-hooks` is supplied.

## Target Ownership

`.harness/install-record.json` records the exact expected plan and each asset as `harness`, `shared`, or `target` owned for refresh purposes.
The validator rejects a complete record whose asset inventory differs from that persisted plan.
The installer may refresh an unchanged harness-owned file when its plugin source changes.
User drift becomes a target-owned conflict until the maintainer chooses one of two explicit resolutions:

- Use `--force` to replace the target with the plugin source and restore harness ownership.
- Use `--adopt <path>` to preserve legitimate target truth and record the file as target owned.

Later full refreshes preserve adopted target-owned content.
Root `AGENTS.md` and `CLAUDE.md` contracts are shared: the installer owns only their managed blocks and preserves target-authored content outside those blocks.
Seeds and preserved pre-existing files remain target owned.
Documented host policy controls optional CI file removal.

## Install Harness Assets

From a target repository, ask Claude Code to use the `harness-install` skill with an explicit stack mode: `gradle`, `maven`, `uv`, `bun`, or `shell`.

The skill invokes `skills/harness-install/scripts/install-harness.ts` with the target repository as `--target`.
It requires the selected stack mode, copies repository-level files and `.claude/` assets, then prints the stack-specific validation command.
Supported modes are `gradle`, `maven`, `uv`, `bun`, and `shell`.

The installer requires `--ci-host` to select which CI files to create.
`--ci-host github` writes `.github/workflows/<tool>.yaml`, `--ci-host gitlab` writes `.gitlab-ci.yml`, `--ci-host both` writes both, and `--ci-host none` writes neither.
CI files are stack-specific initial install assets selected by `--ci-host`.
Installed `pre-push` hooks may run a stronger stack final check.
Hook templates remain inactive unless the installer receives `--activate-hooks`.

## CI Host Selection

The installer requires an explicit CI host selection via `--ci-host`:

- GitHub-only repositories: pass `--ci-host github` to write only `.github/workflows/<tool>.yaml`.
- GitLab-only repositories: pass `--ci-host gitlab` to write only `.gitlab-ci.yml`.
- Repositories that mirror to both hosts: pass `--ci-host both` to write both files.
  - Both CI scripts must run the same selected-mode validation command so `harness-validate` does not report drift.
- No-CI targets: pass `--ci-host none` and document the policy in the project README.

`harness-validate` skips absent CI files for documented inactive hosts.
If a CI file is present, it is validated for command parity against the selected stack validation command.

To run the installer directly, pass the target repository explicitly with both stack mode and CI host:

```sh
/path/to/sinon/plugins/harness/skills/harness-install/scripts/install-harness.ts --target /path/to/target-repo --mode uv --ci-host both
```

## Required Repository Structure

The installer creates this target repository structure, and validators require it:

```text
./
+-- AGENTS.md
+-- ARCHITECTURE.md
+-- CLAUDE.md            (imports AGENTS.md)
+-- .gitignore
+-- .worktreeinclude
+-- .mcp.json
+-- .codegraph/
|   +-- .gitignore
+-- .claude/
|   +-- agents/
|   |   +-- implementation.md
|   |   +-- review.md
|   |   +-- scoped-implementer.md
|   +-- settings.json
|   +-- skills/
|       +-- autonomous-execution/
|       |   +-- SKILL.md
|       +-- issue-mining/
|       |   +-- SKILL.md
|       +-- review/
|           +-- SKILL.md
+-- .agents/
|   +-- skills/         -> .claude/skills/
+-- .codex/
|   +-- agents/
|       +-- implementation.toml
|       +-- review.toml
|       +-- scoped-implementer.toml
+-- docs/
|   +-- design-docs/
|   |   +-- core-beliefs.md
|   |   +-- repository-layout.md
|   +-- exec-plans/
|   |   +-- active/
|   |   +-- completed/
|   |   +-- tech-debt-tracker.md
|   +-- generated/
|   +-- product-specs/
|   |   +-- new-user-onboarding.md
|   +-- references/
|   |   +-- openai-harness-engineering.md
|   |   +-- README.md
|   |   +-- symphony-spec.md
|   +-- templates/
|   |   +-- docs/
|   |   |   +-- AGENTS.md
|   +-- DESIGN.md
|   +-- FRONTEND.md
|   +-- PLANS.md
|   +-- PRODUCT_SENSE.md
|   +-- QUALITY_SCORE.md
|   +-- RELIABILITY.md
|   +-- SECURITY.md
+-- scripts/
    +-- no-box-drawing.ts
    +-- docs-root-files.ts
    +-- exec-plan-links.ts
```

The installed inventory also includes `WORKFLOW.md`, stack-specific validation adapters, CI files when enabled, and inactive hook templates for every stack.
Installed day-to-day runtime surfaces are three project agents (`implementation`, `scoped-implementer`, `review`) and three project skills (`autonomous-execution`, `issue-mining`, `review`).
The general `implementation` agent uses Sonnet/Terra medium for related-file discovery, cross-module or cross-layer changes, design choices, and integrated validation. `scoped-implementer` uses Haiku/Luna low for exhaustive single-file or small related-file edits, and `review` uses Sonnet/Terra medium as a read-only reviewer.
Plugin-root Harness skills and agents remain plugin-owned and are not copied into target repositories.
`WORKFLOW.md` defines branch, review, validation, and publication decisions.
Installed target agents receive workflow decisions through their task prompt.
Only the user-facing top-level or root agent orchestrates work; installed repository agents are delegation targets, and the Harness does not install a `project-orchestrator` agent.
Use the Haiku/Luna low-effort `scoped-implementer` only when the caller supplies an exhaustive single-file or related-file ownership list, exact desired behavior, and targeted validation commands.
Use the Sonnet/Terra medium-effort `implementation` agent when the change is large, spans related files, modules, or layers, requires discovering the complete affected set, or needs cross-file reasoning and integration validation.
An orchestrator MUST explore and plan an ambiguous file set before delegation and MUST NOT send it directly to `scoped-implementer`.
Parallel `scoped-implementer` assignments MUST own disjoint file sets.

Each stack ships POSIX `.githooks/pre-commit` and `.githooks/pre-push` files.
`pre-commit` runs the selected-mode command and may include stack preflight checks.
`pre-push` runs the stack-specific final check, which may be stronger than `pre-commit`.
The installer activates both files only through `git config --local core.hooksPath .githooks/` when `--activate-hooks` is supplied.
Ordinary setup commands such as `bun install`, `uv sync`, `gradle help`, and `mvn validate` MUST NOT activate hooks.
Claude worktrees use Claude Code's default Git worktree behavior.
The selected stack supplies `.worktreeinclude` for portable gitignored local inputs.
Examples include `.env` and `*.local.*` files.
Target `.gitignore` files ignore `.claude/worktrees/`.
The selected stack also supplies `.claude/settings.json`.
The `hooks.PostToolUse[]` entry matches the `EnterWorktree` tool and runs async setup commands from the worktree directory.
All stacks run `codegraph init; codegraph index`.
Bun runs `bun install`.
uv runs `uv sync`.
Gradle runs `./gradlew help`.
Maven runs `./mvnw -q -DskipTests dependency:go-offline`.
Claude Code reports async hook command failures.
It still uses the default worktree creation path.
The project-local `.mcp.json` configures CodeGraph MCP.
`.codegraph/.gitignore` keeps CodeGraph local data out of Git.
`docs/generated/` is a generated-artifact location, not a required database-documentation location.
Generated artifacts SHOULD document their source command, source inputs, freshness, and regeneration trigger.

Target repositories use `AGENTS.md` as the primary harness contract.
`CLAUDE.md` imports the same contract.

## Validation Adapters

| Stack | Detection | Validation command |
| --- | --- | --- |
| Gradle | `settings.gradle(.kts)` or `build.gradle(.kts)` | `./gradlew ktlintCheck` |
| Maven | `pom.xml` | Maven Spotless validation command from the installer summary |
| uv | `uv.lock` or Python `pyproject.toml` | `uv run scripts/check.py` |
| bun | `bun.lock`, `bun.lockb`, or `package.json` | `bun run check` |
| shell | `Makefile` or root-level `*.sh` with no other stack | `sh scripts/check.sh` |

The concrete command is `./gradlew ktlintCheck`, the Maven `./mvnw validate`/`-DspotlessFiles` command, `uv run scripts/check.py`, `bun run check`, or `sh scripts/check.sh`.

Run validation commands from the target repository root.
Gradle, Maven, uv, and shell validators use `markdownlint-cli2` from PATH when it is installed, against installed `.markdownlint-cli2.jsonc` and local rules under `scripts/`.
When `markdownlint-cli2` is absent, those validators print a warning with `bun add -g markdownlint-cli2` installation guidance and skip Markdown linting.
Gradle, Maven, uv, and shell fix commands also run `markdownlint-cli2 --fix` when it is installed.
The Bun validator uses its packaged `markdownlint-cli2` dependency and fails normally if dependency installation is missing or broken.

The Bun validator runs through `bun run check`, a package.json script that runs packaged `markdownlint-cli2` and `ultracite check`.
JavaScript files use oxlint JSDoc tag rules so JSDoc remains usable as type input.
The local oxlint JS plugin rule `tsdoc/require-export-tsdoc` from `scripts/tsdoc-plugin.ts` requires TSDoc only on TypeScript exported APIs.
That means exported top-level functions, exported top-level constants/variables, exported classes, and public methods/accessors on exported classes.

The Bun-side validator and the plugin installer are covered by plugin checks for packaged files, manifest alignment, and syntax-level validation.

The uv validator self-provisions ruff on first use with two commands:

- `uv run --with "ruff>=0.15.18,<0.16.0" ruff check .`
- `uv run --with "ruff>=0.15.18,<0.16.0" ruff format --check .`

It uses Ruff's normal project discovery.
Network access is required on first run.
The ruff binary is then cached.
Markdown validation runs only when `markdownlint-cli2` is available on PATH, as described above.

## Native Tool Enforcement

Each stack uses its native ecosystem tool for validation.
Structural checks that cannot be automated are documented as prose conventions.

| Stack | Validator | Custom extensions | Structural checks |
| --- | --- | --- | --- |
| Gradle/Kotlin | ktlint custom ruleset (family `code`) | buildSrc rules for Kotlin structure, imports, logging, declarations, branching, and doc comments, plus EditorConfig-backed ktlint knobs | File/directory presence, symlinks, hooks, CI command parity, agent/skill frontmatter, execution-plan unchecked tasks |
| Maven/Java | Checkstyle + Spotless (Palantir format) | Checkstyle import, braces, public Javadoc, naming, line-length, and direct `System.out`/`System.err` printing rules plus Spotless format hygiene | File/directory presence, symlinks, hooks, CI command parity, agent/skill frontmatter, execution-plan unchecked tasks |
| uv/Python | ruff defaults + ruff format | prose-only conventions for public naming, docstrings, logging, comments, type-safety, and mutable collections | File/directory presence, symlinks, hooks, CI command parity, agent/skill frontmatter, execution-plan unchecked tasks, shebang-encoding-marker for .py files |
| Bun/TypeScript | ultracite (oxlint linter + oxfmt formatter) | ultracite preset ruleset (ultracite/oxlint/core with `core.ignorePatterns`) + JS-only oxlint JSDoc tag rules + local oxlint JS plugin (`scripts/tsdoc-plugin.ts`: TypeScript exported public API TSDoc) | File/directory presence, symlinks, hooks, CI command parity, agent/skill frontmatter, execution-plan unchecked tasks, shebang-encoding-marker for .ts files |
| Shell | shellcheck + shfmt | Native tools only | File/directory presence, symlinks, hooks, CI command parity, agent/skill frontmatter, execution-plan unchecked tasks |

Gradle installer wiring prepends a `buildSrc/` directory.
Existing `buildSrc/` directories in the target repo MUST be reviewed before install.
The harness expects a fresh `buildSrc/` and will conflict otherwise.

## Diagnostic Format

Validators and checkers emit findings in a canonical format so that agents, CI, and humans can parse them uniformly.

### Location-bearing findings

When a finding maps to a specific position in a source file, the diagnostic prefix MUST follow this shape:

```text
path:line:column [SEVERITY] category: message
```

Lines and columns are one-based.
`SEVERITY` is one of `ERROR`, `WARN`, or `INFO`.
`category` identifies the rule (for example, `classMemberOrdering`, `publicDeclarationDocComment`, `ifStatementBraces`).

### Non-location fallbacks

When file, line, or column information is unavailable, validators use shorter forms:

```text
[SEVERITY] category: repository-level message
```

```text
path/to/file [SEVERITY] category: file-level message
```

Repository-level findings apply to the harness as a whole (for example, missing manifest).
File-level findings apply to a file without a specific position (for example, missing required file).

## Format Commands

Fix commands apply changes through native ecosystem tools:

- Gradle: `./gradlew ktlintFormat` applies Markdown fixes through `markdownlint-cli2 --fix` when it is installed.
  - It then applies ktlint formatting.
- Maven: run `./mvnw exec:exec@format-markdown spotless:apply -DspotlessFiles=<escaped-git-tracked-java-regexes>` with the same escaped, repo-root-anchored patterns as the generated check command.
  - It applies Markdown, Palantir, import-order, unused-import, trailing-whitespace, and final-newline fixes.
  - Checkstyle is a lint-only gate and has no corresponding fix command.
- uv: `uv run scripts/fix.py` applies Markdown fixes through `markdownlint-cli2 --fix` when it is installed.
  - It then applies ruff lint fixes and ruff format.
- Bun: `bun run fix` applies packaged `markdownlint-cli2 --fix`, then runs `ultracite fix`.
- Shell: `sh scripts/fix.sh` applies Markdown fixes through `markdownlint-cli2 --fix` when it is installed.
  - It applies shfmt fixes, then re-runs the shell check and prints any remaining findings.

Fix commands are idempotent and apply fixes only.
The shell fix command additionally re-runs its check.
Run the stack check command afterward.

## Git Hooks

The copied `pre-commit` template includes the selected-mode command and may include stack preflight checks.
The copied `pre-push` template runs the stack-specific final check and may include tests or broader verification.

The installer does not write into `.git/hooks/`.
Every mode copies executable POSIX hooks under `.githooks/`.
Without `--activate-hooks`, the installer and ordinary stack setup leave Git configuration unchanged.
With `--activate-hooks`, the installer points the repository-local `core.hooksPath` at `.githooks/`, activating both stages atomically and explicitly.

## Metadata and Attribution

- Manifest author: `ririnto`, matching the repository owner metadata.
- Plugin license: Apache-2.0, recorded in `LICENSE`.
- External inspiration and adapted taxonomy are documented in `THIRD_PARTY_NOTICES.md`.
- The marketplace catalog in `.claude-plugin/marketplace.json` and all plugin manifests and entries intentionally omit `version`, so git SHA is the plugin update key.

## Harness Evolution

The installed harness MAY evolve as the project moves through discovery, implementation, hardening, release, and maintenance.
Treat the current committed harness files as the active target contract.
Use `harness-evolve` when repeated validation or review failures show that repository templates, docs, agents, skills, generated-artifact locations, or validation rules should change.

## Layout

The plugin root contains `.claude-plugin/plugin.json`, `LICENSE`, `README.md`, `THIRD_PARTY_NOTICES.md`, `agents/`, `scripts/`, and `skills/`.
The `skills/` directory contains `harness-evolve/`, `harness-install/`, and `harness-validate/`.

## Scope Notes

- This plugin prepares repository knowledge, guardrails, templates, and validation paths.
- Installed agents are starting points, not immutable plugin internals.
- The plugin skill `harness-validate` validates packaged and installed harness assets before or after installation.
- Installed repositories run validation through their stack command and update records through `WORKFLOW.md`.
- GitHub Actions and GitLab CI files use ordinary version tags from the archive.
  - Projects with strict supply-chain policy SHOULD pin actions and images to reviewed immutable references after installation.
- Maven, Gradle, Python, and test self-checks may create cache, IDE, or build metadata under asset directories.
  - Repository `.gitignore` and installer filters exclude ignored byproducts.
- The bun asset wrapper validates JavaScript and TypeScript files through ultracite, JavaScript-only oxlint JSDoc tag rules, and a local oxlint JS plugin that requires TSDoc on TypeScript exported public API declarations.
- The uv asset wrapper validates and formats Python files through Ruff's normal project discovery.
- The Maven/Java stack uses Spotless for format-only fixes (`removeUnusedImports`/`trimTrailingWhitespace`/`endWithNewline`).
  - Checkstyle provides lint-only Java structure gates that are safe to enforce without Java-specific custom source code.
