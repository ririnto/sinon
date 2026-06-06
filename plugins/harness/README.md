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

- `scripts/plugin-self-check.sh` validates packaged and tracked plugin files: per-stack asset presence, hook-sync wiring, CI command parity, style hardening contracts (no leading-underscore declarations in implementation files, no one-line Python docstrings on public symbols, no static `SKIP_TREE_PARTS` path filtering), and native-tool smoke checks (`bunx`, `uv`, `shellcheck`, `shfmt` version probes). It skips a stack gracefully when its toolchain is absent from PATH.
- `skills/harness-install/scripts/install-harness.py` discovers installable assets via `git ls-files` rather than hardcoded directory-name filtering, ensuring only version-controlled files enter the install plan.
- `skills/harness-install/assets/` contains files the installer copies into target repositories, including `.claude/agents`, `.claude/skills`, `docs/harness`, docs, CI, validation adapters, and Git hook scaffolds.

## Runtime Model

The Claude Code manifest declares only `./skills/`. Plugin-root agents remain in `agents/` as optional structural specialists for changing the target repository's harness contract, but they are not declared in `.claude-plugin/plugin.json` and are not copied into target repositories. Target repository agents, project skills, docs, CI snippets, validators, and hook scaffolds are packaged under `skills/harness-install/assets/` and become target-owned only after installation. The plugin does not expose top-level hooks; packaged hook scaffolds live under `skills/harness-install/assets/common/docs/harness/git-hooks/`, and the installer copies the scaffold sources before rendering selected-mode pre-commit and pre-push hook templates.

## Target Ownership

Target repositories own every installed harness file. Copied docs, scripts, CI files, hooks, agents, skills, templates, and validation adapters MAY be edited, renamed, or removed only through harness evolution that keeps manifest, docs, validators, CI, and hook policy aligned; optional CI renderings may be deleted under documented host policy.

## Install Harness Assets

From a target repository, ask Claude Code to use the `harness-install` skill with an explicit stack mode: `gradle`, `maven`, `uv`, `bun`, or `shell`.

The skill invokes `skills/harness-install/scripts/install-harness.py` with the target repository as `--target`, requires the selected stack mode, copies repository-level files and `.claude/` assets, then prints the stack-specific validation command. Supported modes are `gradle`, `maven`, `uv`, `bun`, and `shell`.

The installer requires `--ci-host` to select which CI files to create: `--ci-host github` writes `.github/workflows/<tool>.yaml`, `--ci-host gitlab` writes `.gitlab-ci.yml`, `--ci-host both` writes both, and `--ci-host none` writes neither. Both CI snippets run the same selected-mode command as generated `pre-push`; the value is rendered from `{{validation_command}}` into target docs and hooks.

## CI Host Selection

The installer requires an explicit CI host selection via `--ci-host`:

- GitHub-only repositories: pass `--ci-host github` to write only `.github/workflows/<tool>.yaml`.
- GitLab-only repositories: pass `--ci-host gitlab` to write only `.gitlab-ci.yml`.
- Repositories that mirror to both hosts: pass `--ci-host both` to write both files; the generated `pre-push` final check command must match both CI scripts so `harness-validate` does not report drift.
- Targets that do not run CI: pass `--ci-host none` and document the policy in the project README.

`harness-validate` skips absent CI files for documented inactive hosts; if a CI file is present, it is validated for command parity against the generated `pre-push` command.

To run the installer directly, pass the target repository explicitly with both stack mode and CI host:

```sh
/path/to/sinon/plugins/harness/skills/harness-install/scripts/install-harness.py --target /path/to/target-repo --mode uv --ci-host both
```

## Required Repository Structure

The installer creates this repository context structure, and validators require it:

```text
AGENTS.md
ARCHITECTURE.md
CLAUDE.md
.claude/
|-- agents/
|   |-- harness-implementation-agent.md
|   |-- harness-orchestrator.md
|   `-- harness-review-agent.md
`-- skills/
    |-- harness-orchestrate/
    |   `-- SKILL.md
    |-- harness-review/
    |   `-- SKILL.md
    `-- harness-validate/
        `-- SKILL.md
docs/
|-- design-docs/
|   `-- core-beliefs.md
|-- exec-plans/
|   |-- active/
|   |   `-- .gitkeep
|   |-- completed/
|   |   `-- .gitkeep
|   `-- tech-debt-tracker.md
|-- generated/
|   `-- .gitkeep
|-- harness/
|   |-- README.md
|   |-- git-hooks/
|   |   |-- pre-commit
|   |   `-- pre-push
|   `-- templates/
|-- product-specs/
|   `-- new-user-onboarding.md
|-- references/
|   |-- openai-harness-engineering.md
|   |-- README.md
|   `-- symphony-spec.md
|-- DESIGN.md
|-- FRONTEND.md
|-- PLANS.md
|-- PRODUCT_SENSE.md
|-- QUALITY_SCORE.md
|-- RELIABILITY.md
`-- SECURITY.md
```

Empty required directories are kept in version control with `.gitkeep`. `docs/harness/git-hooks/pre-commit` and `docs/harness/git-hooks/pre-push` are generated, target-owned hook templates that both run the selected-mode command rendered from `{{validation_command}}`; each stack's check command syncs these templates into the active hooks directory (resolved via `git rev-parse --git-path hooks`) when it runs, so the hooks activate on the first local check rather than at install time. `docs/generated/` is a generated-artifact location, not a required database-documentation location. Generated artifacts SHOULD document their source command, source inputs, freshness, and regeneration trigger.

In fresh installed target repositories, `CLAUDE.md` is the primary harness contract and Claude Code entry point, and `AGENTS.md` is a symlink alias to `CLAUDE.md`. When refreshing an existing AGENTS-only repository, the installer may preserve `AGENTS.md` as the real file and add `CLAUDE.md` as the symlink alias instead. In either orientation, runtimes that load either filename resolve to the same document.

## Validation Adapters

| Stack | Detection | Validation command |
| --- | --- | --- |
| Gradle | `settings.gradle(.kts)` or `build.gradle(.kts)` | `{{validation_command}}` |
| Maven | `pom.xml` | `{{validation_command}}` |
| uv | `uv.lock` or Python `pyproject.toml` | `{{validation_command}}` |
| bun | `bun.lock`, `bun.lockb`, or `package.json` | `{{validation_command}}` |
| shell | `Makefile` or root-level `*.sh` with no other stack | `{{validation_command}}` |

Each installed mode renders `{{validation_command}}` to the concrete command (`./gradlew ktlintCheck`, the Maven `./mvnw validate`/`-DspotlessFiles` command, `uv run scripts/check.py`, `bun run check`, or `sh scripts/check.sh`) in target common assets.

Run validation commands from the target repository root. The shell validator is a portable POSIX shell script and requires both `shellcheck` and `shfmt` on PATH.

The bun validator runs through `bun run check` (a package.json script that runs `sh scripts/check.sh`, which syncs Git hooks, prepares dev dependencies with `bun install --no-save`, then runs `bunx ultracite check --` followed by `bun scripts/validate-jsdoc.mjs`; the Bun-side validator requires JSDoc on top-level functions, constants, variables, and class methods with concrete JSDoc types (no `{object}`/`{Object}`) and rejects broad object types.

The Bun-side validator and the plugin installer are style-hardened by plugin checks: `sh plugins/harness/scripts/plugin-self-check.sh` enforces no leading-underscore public declarations in these implementation files, no one-line public Python docstrings, and concrete JSDoc type requirements in `validate-jsdoc.mjs`.

The uv validator self-provisions ruff on first use via `uvx --with "ruff>=0.15.16,<0.16.0" ruff check` and `ruff format --check` over Git-tracked Python files, so network access is required on first run; the ruff binary is then cached. No separate Node.js or other runtime is required beyond `uv` itself.

## Native Tool Enforcement

Each stack uses its native ecosystem tool for validation. Structural checks that cannot be automated are documented as prose conventions.

| Stack | Validator | Custom extensions | Structural checks |
| --- | --- | --- | --- |
| Gradle/Kotlin | ktlint custom ruleset (family `code`) | 13 rules in buildSrc (if-statement-braces, import-over-fqn, kotlin-top-level-declaration-count, implicit-lambda-it, leading-underscore, unchecked-cast-suppression, non-null-assertion, multiline-doc-style, unstructured-logging, companion-object-position, explicit-property-type, terminal-branch-when, public-declaration-doc-comment) + 2 EditorConfig knobs | File/directory presence, symlinks, hooks, CI command parity, agent/skill frontmatter, execution-plan unchecked tasks |
| Maven/Java | Checkstyle + Spotless (Palantir format) | Checkstyle import/braces linting plus Spotless format hygiene | File/directory presence, symlinks, hooks, CI command parity, agent/skill frontmatter, execution-plan unchecked tasks |
| uv/Python | ruff (`F403` + defaults) | 7 code conventions are prose-only (leading-underscore, multiline-doc-style, unstructured-logging, public-declaration-doc-comment, unchecked-cast-suppression, triple-quote-inline-comment, mutable-collection) — no automated enforcement | File/directory presence, symlinks, hooks, CI command parity, agent/skill frontmatter, execution-plan unchecked tasks, shebang-encoding-marker for .py files |
| Bun/TypeScript | ultracite (oxlint linter + oxfmt formatter) | ultracite preset ruleset (ultracite/oxlint/core) + jsdoc plugin rules (`require-param`, `require-param-type`, `require-param-name`, `require-returns`, `require-returns-type`) + Bun-side JSDoc validator (`scripts/validate-jsdoc.mjs`: missing JSDoc on functions/variables/methods, broad `{object}`/`{Object}` type ban) | File/directory presence, symlinks, hooks, CI command parity, agent/skill frontmatter, execution-plan unchecked tasks, shebang-encoding-marker for .ts files |
| Shell | shellcheck + shfmt | Native tools only | File/directory presence, symlinks, hooks, CI command parity, agent/skill frontmatter, execution-plan unchecked tasks |

Gradle installer wiring prepends a `buildSrc/` directory. Existing `buildSrc/` directories in the target repo MUST be reviewed before install; the harness expects a fresh `buildSrc/` and will conflict otherwise.

## Diagnostic Format

Validators and checkers emit findings in a canonical format so that agents, CI, and humans can parse them uniformly.

### Location-bearing findings

When a finding maps to a specific position in a source file, the diagnostic prefix MUST follow this shape:

```text
path:line:column [SEVERITY] category: message
```

Lines and columns are one-based. `SEVERITY` is one of `ERROR`, `WARN`, or `INFO`. `category` identifies the rule (for example, `classMemberOrdering`, `publicDeclarationDocComment`, `ifStatementBraces`).

### Non-location fallbacks

When file, line, or column information is unavailable, validators use shorter forms:

```text
[SEVERITY] category: repository-level message
```

```text
path/to/file [SEVERITY] category: file-level message
```

Repository-level findings apply to the harness as a whole (for example, missing manifest). File-level findings apply to a file without a specific position (for example, missing required file).

## Format Commands

Fix commands apply changes through native ecosystem tools:

- Gradle: `./gradlew ktlintFormat` applies ktlint's built-in formatting.
- Maven: run `./mvnw spotless:apply -DspotlessFiles=<escaped-git-tracked-java-regexes>` with the same escaped, repo-root-anchored patterns as the generated check command to apply Palantir format and import order to Git-tracked Java files; Checkstyle is a lint-only gate and has no corresponding fix command.
- uv: `uv run scripts/fix.py` applies ruff lint fixes, then ruff format (double-quote style, line length, trailing commas).
- Bun: `bun run fix` prepares dev dependencies with `bun install --no-save`, then runs `bunx ultracite fix --` on Git-tracked JavaScript and TypeScript files.
- Shell: `sh scripts/fix.sh` applies shfmt fixes, then re-runs the shell check and prints any remaining findings.

Fix commands are idempotent: a second run produces no additional modifications. Each fix command applies fixes only (the shell fix command additionally re-runs its check); run the stack check command afterward to verify that no ERROR-level findings remain.

## Git Hooks

The installer writes two selected-mode hook templates under `docs/harness/git-hooks/` (tracked in the repository). Both `pre-commit` and `pre-push` run the selected-mode command rendered from `{{validation_command}}`.

The installer does not write into `.git/hooks/`. Instead, every stack's check command syncs the tracked templates into the active hooks directory each time it runs: it resolves the directory through `git rev-parse --git-path hooks` (so linked worktrees and custom Git layouts resolve correctly), copies any `pre-commit`/`pre-push` whose content differs, and marks them executable. Gradle and Maven perform this sync through the build tool (a `ktlintCheck` `doLast` action and a `maven-antrun-plugin` execution bound to `validate`); uv, bun, and shell perform it inside their check scripts. Hooks therefore activate after the first local check run rather than at install time, and they stay in sync automatically as the tracked templates change.

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
- The bun asset wrapper validates Git-tracked JavaScript and TypeScript files through ultracite plus a Bun-side JSDoc validator (`scripts/validate-jsdoc.mjs`) that requires JSDoc on functions, variables, and class methods and rejects broad `{object}`/`{Object}` JSDoc types; generated dependency directories such as `node_modules/` are ignored by Git and are not package contents.
- The uv asset wrapper validates and formats Git-tracked Python files through ruff; the remaining Python conventions documented as prose-only rules are not enforced by a packaged AST runtime.
- The Maven/Java stack uses Spotless for format-only fixes (`removeUnusedImports`/`trimTrailingWhitespace`/`endWithNewline`). Code-structure detection rules are documented as prose conventions because the project deliberately avoids a full Java formatter or Checkstyle/PMD integration.
