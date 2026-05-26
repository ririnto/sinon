---
description: >-
  Overview of the Harness plugin, its install, validation, evolution, templates, agents, skills, CI, and Git hook scaffolding workflow.
---

# Harness

Harness is a Claude Code plugin for installing, validating, and evolving repository-owned agent development scaffolding. It combines the v6 archive structure with this repository's plugin packaging rules: skills are the declared runtime surface, plugin-root agents are structural harness specialists for changing a target repository's harness contract, and files that should live inside a target repository are packaged only under `skills/harness-install/assets/`.

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

## Plugin-Owned Structural Agents

These agents are plugin-owned advisory surfaces for the harness lifecycle itself. They are valid when the task is to design, review, or verify the target repository's harness structure; they are not installed as day-to-day target repository agents.

- `harness-architect`: plan target harness structure, ownership boundaries, and validation alignment.
- `harness-reviewer`: review target harness assets and evolution proposals for contract drift.
- `harness-validator`: diagnose target harness validation output and readiness gates.

## Included Commands

This plugin ships no commands.

## Packaged Scripts and Assets

- `scripts/plugin-self-check.sh` validates packaged and tracked plugin files. It also runs four-stack runtime smoke checks (uv import via `uv run --with libcst`, bun dynamic `import('./harness-check.ts')`, gradle `buildSrc:compileKotlin`, mvn `validate`) and skips each stack gracefully when the toolchain is absent.
- `skills/harness-install/assets/` contains files the installer copies into target repositories, including `.claude/agents`, `.claude/skills`, `docs/harness`, docs, CI, validation adapters, and Git hook scaffolds.

## Runtime Model

The Claude Code manifest declares only `./skills/`. Plugin-root agents remain in `agents/` as optional structural specialists for changing the target repository's harness contract, but they are not declared in `.claude-plugin/plugin.json` and are not copied into target repositories. Target repository agents, project skills, docs, CI snippets, validators, and hook scaffolds are packaged under `skills/harness-install/assets/` and become target-owned only after installation. The plugin does not expose top-level hooks; packaged hook scaffolds live under `skills/harness-install/assets/common/docs/harness/git-hooks/`, and the installer copies the scaffold sources before rendering selected-mode pre-commit and pre-push hook templates.

## Target Ownership

Target repositories own every installed harness file. Copied docs, scripts, CI files, hooks, agents, skills, templates, and validation adapters MAY be edited, renamed, or removed to fit the target project. The installed `docs/harness/manifest.json` is the target repository harness metadata contract; it replaces the old `docs/harness/config.json` convention used by earlier drafts of this plugin.

## Install Harness Assets

From a target repository, ask Claude Code to use the `harness-install` skill with stack mode `auto` or an explicit mode such as `gradle`, `maven`, `uv`, `bun`, or `shell`.

The skill invokes `skills/harness-install/scripts/install-harness.sh` with the target repository as `--target`, detects or accepts the stack mode, copies repository-level files and `.claude/` assets, then prints the stack-specific validation command. Supported modes are `auto`, `gradle`, `maven`, `uv`, `bun`, and `shell`.

By default the installer writes both GitHub Actions and GitLab CI examples for the selected stack: `.github/workflows/harness.yml` and `.gitlab-ci.yml`. Both CI snippets are rendered from templates and run the same final check command as generated `pre-push`; for Gradle this is `check`. Pass `--no-ci` to skip both CI files.

## CI Host Selection

The installer ships both CI examples so it never has to guess where the target publishes builds. Pick the active host as a post-install step:

- GitHub-only repositories SHOULD delete `.gitlab-ci.yml` after install and keep `.github/workflows/harness.yml`.
- GitLab-only repositories SHOULD delete `.github/workflows/harness.yml` after install and keep `.gitlab-ci.yml`.
- Repositories that mirror to both hosts MAY keep both files; the generated `pre-push` final check command must match both CI scripts so `harness-validate` does not report drift.
- Targets that do not run CI at all SHOULD pass `--no-ci` on install and document the policy in the project README.

`harness-validate` reports `.github/workflows/harness.yml` and `.gitlab-ci.yml` as missing rather than mismatched when they are intentionally absent for the selected host.

To run the installer directly, pass the target repository explicitly:

```sh
sh /path/to/sinon/plugins/harness/skills/harness-install/scripts/install-harness.sh --target /path/to/target-repo --mode uv --hooks none
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

Empty required directories are kept in version control with `.gitkeep`. `docs/harness/git-hooks/pre-commit` is a generated, target-owned hook template: Gradle uses it for `harnessCheck`, while non-Gradle stacks use it for compliance checks. `docs/harness/git-hooks/pre-push` is the generated final-check hook template: Gradle uses `check`, while non-Gradle stacks use the selected validation command. Neither is an active Git hook unless the target repository opts in. `docs/generated/` is a generated-artifact location, not a required database-documentation location. Generated artifacts SHOULD document their source command, source inputs, freshness, and regeneration trigger.

In installed target repositories, `AGENTS.md` is the primary harness contract. `CLAUDE.md` is retained as the Claude Code entry point and points back to `AGENTS.md`.

## Validation Adapters

| Stack | Detection | Validation command |
| --- | --- | --- |
| Gradle local harness validation | `settings.gradle(.kts)` or `build.gradle(.kts)` | `./gradlew harnessCheck`, or `gradle harnessCheck` when the target uses system Gradle without a wrapper |
| Gradle final check | `settings.gradle(.kts)` or `build.gradle(.kts)` | `./gradlew check`, or `gradle check` when the target uses system Gradle without a wrapper |
| Maven | `pom.xml` | `mvn -q -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:check` |
| uv | `uv.lock` or Python `pyproject.toml` | `uv run --script docs/harness/uv/harness_check.py` |
| bun | `bun.lock`, `bun.lockb`, or `package.json` | `bun --install=fallback run docs/harness/bun/harness-check.ts` |
| shell | `Makefile` or root-level `*.sh` with no other stack | `sh docs/harness/shell/harness-check.sh` |

Run validation commands from the target repository root. The uv, bun, Maven, and shell validators bind that current directory as the target root, and native validators compare the installed `docs/harness/manifest.json` fields that this plugin writes. The shell adapter implements a minimum-viable subset (file/directory existence, hook shebang/executable, scaffold-leak scan, completed-plan unchecked-task scan) and requires `python3` available on PATH for JSON parsing of the manifest.

## Language-Specific Validator Coverage

Each install mode now ships the manifest slice for its selected language or runtime, so target repositories receive only the add-ons their installed validator understands. Shared structural checks remain common across slices; code-structure checks stay stack-specific.

AST/PSI validators use semantic tree traversal for code-structure rules rather than per-check CLI switches or regex-only scans. Inspired by the LY Tech Blog AST validation posture, formatting can remain a separate concern while structural validators compare declarations, ownership, and member order in the parsed tree, leaving room for before/after tree validation through the existing manifest-driven categories.

| Mode | Structural parser | Code-order coverage |
| --- | --- | --- |
| Gradle/Kotlin+Java | Kotlin PSI through the Gradle worker classloader plus JavaParser for Java sources | Kotlin class member ordering, Java class member ordering, companion-object position, top-level declaration shape, terminal Kotlin if/else-to-when enforcement, and Kotlin code-style checks. Kotlin enum entries and Java enum constants are treated as language-mandated enum preamble items, not ordinary sortable members. |
| Maven/Java | JavaParser inside the Maven plugin | Java class member ordering and Java code-style checks. Java enum constants are treated as language-mandated enum preamble items, not ordinary sortable members. Maven+Kotlin source validation is not currently enabled because the Maven adapter does not embed Kotlin PSI. |
| uv/Python | LibCST for Python source checks | Python code-style checks for the selected uv slice. |
| Bun/TypeScript | TypeScript compiler API | TypeScript code-style checks for the selected Bun slice. |
| Shell | POSIX shell plus `python3` for manifest reads | Portable baseline checks only: files, directories, hooks, scaffold leaks, and completed-plan tasks. |

Gradle installer wiring prepends a `buildSrc/` directory. Existing `buildSrc/` directories in the target repo MUST be reviewed before install; the harness expects a fresh `buildSrc/` and will conflict otherwise.

## Diagnostic Format

Validators and checkers emit findings in a canonical format so that agents, CI, and humans can parse them uniformly.

### Location-bearing findings

When a finding maps to a specific position in a source file, the diagnostic prefix MUST follow this shape:

```text
path:line:column [SEVERITY] category: message
```

Lines and columns are one-based. `SEVERITY` is one of `ERROR`, `WARN`, or `INFO`. `category` is the manifest rule identifier (for example, `classMemberOrdering`, `greaterThanComparison`, `leafFunctionBlankLines`).

### Non-location fallbacks

When file, line, or column information is unavailable, validators use shorter forms:

```text
[SEVERITY] category: repository-level message
```

```text
path/to/file [SEVERITY] category: file-level message
```

Repository-level findings apply to the harness as a whole (for example, missing manifest). File-level findings apply to a file without a specific position (for example, missing required file).

## Safe-Format Contracts

`harnessFormat` applies only explicitly allowlisted safe fixes. A safe fix is a deterministic, semantics-preserving edit that does not change program behavior. The allowlist is:

| Rule category | Bun | uv | Gradle | Maven | Mutation type |
| --- | --- | --- | --- | --- | --- |
| `leafFunctionBlankLines` | Allow | Allow | Defer | Defer | Remove blank lines inside leaf function bodies |
| `emptyDirectoryPlaceholders` | Allow | Allow | Defer | Defer | Create `.gitkeep` in empty required directories |
| `envShebangUsage` | Allow | Allow | Defer | Defer | Replace script shebang with `/usr/bin/env` form |
| `hookGeneratedMarker` | Allow | Allow | Defer | Defer | Insert generated marker in managed hook template |
| `hookShebang` | Allow | Allow | Defer | Defer | Replace missing or incorrect hook shebang |
| `shebangEncodingMarker` | Allow | Allow | Defer | Defer | Insert encoding marker after shebang |

Rules not in this table are not formatted. `harnessFormat` is idempotent: a second run immediately after the first produces no additional modifications.

Gradle and Maven formatting is deferred and not implemented in this change set.

### Shell exclusion

The shell runtime ships `harness-check.sh`. Shell `harnessFormat` is added in a follow-up phase. Shell validation covers file presence, directory structure, hook shebangs and executable bits, scaffold-leak scanning, and completed-plan unchecked-task scanning.

## Git Hooks

The installer writes two selected-mode hook templates in `docs/harness/git-hooks/` on fresh install. Gradle `pre-commit` runs `harnessCheck` for intermediate harness feedback, and Gradle `pre-push` runs `check` for the final push gate. Non-Gradle `pre-commit` performs lightweight harness-rule compliance checks, and non-Gradle `pre-push` runs the selected validation command. Managed generated templates refresh with the selected intermediate and final commands; custom target-owned templates are preserved unless `--force` is used. Git hook activation is opt-in because it modifies local Git behavior outside version control.

```sh
sh /path/to/sinon/plugins/harness/skills/harness-install/scripts/install-harness.sh --target /path/to/target-repo --mode uv --hooks copy
```

Use `--hooks none` or omit the flag to skip Git hook activation. Use `--hooks copy` only when the target should copy both generated hooks to `pre-commit` and `pre-push` in the active worktree hooks directory.

Copy mode keeps existing active local hooks unless `--force` is used. With `--force`, the installer replaces both active local hooks with installer-generated content for the selected mode. Use `--hooks copy --force` only with explicit approval because it changes local Git behavior.

### Worktree-aware hook installation

`--hooks copy` resolves the active hooks directory through `git rev-parse --git-common-dir`. In a primary worktree this is `.git/hooks/`. In a linked worktree created by `git worktree add`, `.git` is a file that points back to the main worktree's `.git/worktrees/<name>/` directory; the installer still installs into the shared `<main>/.git/hooks/` so the same hooks fire from every worktree of the same repository. The installer refuses to write through a `.git` symlink or when the worktree is not initialized.

### Recommended pattern: harness-tracked hooks via `core.hooksPath`

The harness ships `pre-commit` and `pre-push` under `docs/harness/git-hooks/`, which is committed to the repository. Point Git at that directory to activate the hooks for the current clone without copying anything:

```sh
git config core.hooksPath docs/harness/git-hooks
```

With this config, generated hook content evolves through normal version control: every worktree of the same clone sees the same hooks, and `harness-install` refreshes the active hooks in place when re-run with `--hooks none`. The installer refuses `--hooks copy` while `core.hooksPath` already resolves to `docs/harness/git-hooks/` because copying to the worktree hooks directory would not activate the worktree hooks.

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
- Maven, Gradle, Python, and test self-checks may create cache, IDE, or build metadata under asset directories. Repository `.gitignore` and installer filters exclude ignored byproducts such as `__pycache__/`, `.pytest_cache/`, `*.pyc`, `.classpath`, `.project`, `.factorypath`, `.settings/`, `.gradle/`, `bin/`, `build/`, and `target/`; plugin self-checks validate packaged/tracked asset files rather than failing on ignored working-tree byproducts.
