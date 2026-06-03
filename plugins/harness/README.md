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

- `scripts/plugin-self-check.sh` validates packaged and tracked plugin files. It also runs five-stack runtime smoke checks (uv import via `uv run --with libcst`, bun dynamic `import('./harness-check.ts')`, shell fixture validation, gradle `buildSrc:compileKotlin`, mvn `validate`) and skips external-tool stacks gracefully when the toolchain is absent.
- `skills/harness-install/assets/` contains files the installer copies into target repositories, including `.claude/agents`, `.claude/skills`, `docs/harness`, docs, CI, validation adapters, and Git hook scaffolds.

## Runtime Model

The Claude Code manifest declares only `./skills/`. Plugin-root agents remain in `agents/` as optional structural specialists for changing the target repository's harness contract, but they are not declared in `.claude-plugin/plugin.json` and are not copied into target repositories. Target repository agents, project skills, docs, CI snippets, validators, and hook scaffolds are packaged under `skills/harness-install/assets/` and become target-owned only after installation. The plugin does not expose top-level hooks; packaged hook scaffolds live under `skills/harness-install/assets/common/docs/harness/git-hooks/`, and the installer copies the scaffold sources before rendering selected-mode pre-commit and pre-push hook templates.

## Target Ownership

Target repositories own every installed harness file. Copied docs, scripts, CI files, hooks, agents, skills, templates, and validation adapters MAY be edited, renamed, or removed only through harness evolution that keeps manifest, docs, validators, CI, and hook policy aligned; optional CI renderings may be deleted under documented host policy.

## Install Harness Assets

From a target repository, ask Claude Code to use the `harness-install` skill with an explicit stack mode: `gradle`, `maven`, `uv`, `bun`, or `shell`.

The skill invokes `skills/harness-install/scripts/install-harness.sh` with the target repository as `--target`, requires the selected stack mode, copies repository-level files and `.claude/` assets, then prints the stack-specific validation command. Supported modes are `gradle`, `maven`, `uv`, `bun`, and `shell`.

By default the installer writes both GitHub Actions and GitLab CI examples for the selected stack: `.github/workflows/harness.yml` and `.gitlab-ci.yml`. Both CI snippets are rendered from templates and run the same final check command as generated `pre-push`; for Gradle this is `check`. Pass `--no-ci` to skip both CI files.

## CI Host Selection

The installer ships both CI examples so it never has to guess where the target publishes builds. Pick the active host as a post-install step:

- GitHub-only repositories SHOULD delete `.gitlab-ci.yml` after install and keep `.github/workflows/harness.yml`.
- GitLab-only repositories SHOULD delete `.github/workflows/harness.yml` after install and keep `.gitlab-ci.yml`.
- Repositories that mirror to both hosts MAY keep both files; the generated `pre-push` final check command must match both CI scripts so `harness-validate` does not report drift.
- Targets that do not run CI at all SHOULD pass `--no-ci` on install and document the policy in the project README.

`harness-validate` skips absent CI files for documented inactive hosts; if a CI file is present, it is validated for command parity against the generated `pre-push` command.

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
|   |-- manifest.json
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

Empty required directories are kept in version control with `.gitkeep`. `docs/harness/git-hooks/pre-commit` is a generated, target-owned hook template: Gradle uses it for `harnessCheck`, while non-Gradle stacks use it for compliance checks. `docs/harness/git-hooks/pre-push` is the generated final-check hook template: Gradle uses `check`, while non-Gradle stacks use the selected validation command. Neither is an active Git hook unless the target repository opts in. `docs/generated/` is a generated-artifact location, not a required database-documentation location. Generated artifacts SHOULD document their source command, source inputs, freshness, and regeneration trigger.

In fresh installed target repositories, `CLAUDE.md` is the primary harness contract and Claude Code entry point, and `AGENTS.md` is a symlink alias to `CLAUDE.md`. When refreshing an existing AGENTS-only repository, the installer may preserve `AGENTS.md` as the real file and add `CLAUDE.md` as the symlink alias instead. In either orientation, runtimes that load either filename resolve to the same document.

## Validation Adapters

| Stack | Detection | Validation command |
| --- | --- | --- |
| Gradle local harness validation | `settings.gradle(.kts)` or `build.gradle(.kts)` | `./gradlew harnessCheck`, or `gradle harnessCheck` when the target uses system Gradle without a wrapper |
| Gradle final check | `settings.gradle(.kts)` or `build.gradle(.kts)` | `./gradlew check`, or `gradle check` when the target uses system Gradle without a wrapper |
| Maven | `pom.xml` | `mvn -q -f harness-maven-plugin/pom.xml install com.ririnto.sinon:harness-maven-plugin:0.1.0:check` |
| uv | `uv.lock` or Python `pyproject.toml` | `uv run --script docs/harness/uv/harness_check.py` |
| bun | `bun.lock`, `bun.lockb`, or `package.json` | `bun --install=fallback run docs/harness/bun/harness-check.ts` |
| shell | `Makefile` or root-level `*.sh` with no other stack | `sh docs/harness/shell/harness-check.sh` |

Run validation commands from the target repository root. The uv, bun, Maven, and shell validators bind that current directory as the target root, and native validators compare the installed `docs/harness/manifest.json` fields that this plugin writes. The shell adapter implements a portable subset (file/directory existence, hook shebang/executable, hook command parity, CI command parity, symlink/path safety for manifest-controlled filesystem paths, scaffold-leak scan, completed-plan unchecked-task scan, and shellcheck) and requires `python3` available on PATH for JSON parsing of the manifest.

The bun validator self-provisions pinned tools on first use via `bunx oxlint@1.68.0` and `bunx oxfmt@0.53.0`, so network access is required the first time `check` or `format` runs. The 3 custom oxlint rules execute on bun's own JavaScript runtime and do not require a separate Node.js binary; when `bunx` is unavailable the validator skips custom-rule detection and continues.

The uv validator self-provisions `uvx ruff@0.15.15` on first use, so network access is required the first time it runs; the ruff binary is then cached. No separate Node.js or other runtime is required beyond `uv` itself.

## Language-Specific Validator Coverage

Each install mode now ships the manifest slice for its selected language or runtime, so target repositories receive only the add-ons their installed validator understands. Shared structural checks remain common across slices; code-structure checks stay stack-specific.

## Rule Ownership Per Stack

Each stack delegates code-style detection to the most capable tool available (AST parser, ecosystem linter, or custom harness engine) while retaining structural/manifest/filesystem rules in the harness engine. This table clarifies which rules are handled by which subsystem:

| Stack | Structural (harness engine) | Code-style (ecosystem or harness) |
| --- | --- | --- |
| Gradle/Kotlin | 18 (Kotlin buildSrc core engine; no shebangEncodingMarker) | 14 (ktlint: greaterThanComparison, leafFunctionBlankLines, implicitLambdaIt, kotlinTopLevelDeclarationCount, ifStatementBraces, terminalBranchWhen, nonNullAssertion, uncheckedCastSuppression, unstructuredLogging, importOverFqn, publicDeclarationDocComment, leadingUnderscore, multilineDocStyle, companionObjectPosition) |
| Maven/Java | 18 (file presence, directory presence, empty-dir placeholders, templates, docs, agents, skills, hooks, CI parity, script shebangs, unchecked tasks, symlinks; no shebangEncodingMarker) | 11 (JavaParser: greaterThanComparison, leafFunctionBlankLines, unstructuredLogging, wildcardImport, importOverFqn, publicDeclarationDocComment, leadingUnderscore, multilineDocStyle, ifStatementBraces, classMemberOrdering, uncheckedCastSuppression) |
| uv/Python | 19 (structural + shebangEncodingMarker for .py files) | 7 code-style via libcst (greaterThanComparison, leadingUnderscore, multilineDocStyle, unstructuredLogging, publicDeclarationDocComment, uncheckedCastSuppression, tripleQuoteInlineComment) + 1 via ruff (wildcardImport via `F403`) |
| Bun/TypeScript | 19 (structural + shebangEncodingMarker for .ts files) | 6 built-in oxlint rules (no-console, no-empty, curly, no-underscore-dangle, no-namespace, ban-ts-comment) + 3 custom oxlint JS-plugin rules (greaterThanComparison, multilineDocStyle, publicDeclarationDocComment) |
| Shell | 8 (file/dir presence, hooks, CI parity, shebangs, executable bits, symlinks, scaffold leaks, unchecked tasks) | 0 (shell has no AST code-style rules) |

**Note on structural-rule counts:** Structural counts differ slightly per stack: uv and Bun include `shebangEncodingMarker` (a structural rule for `.py`/`.ts` script sources) and so carry 19 structural rules, while Gradle and Maven omit it (Java/Kotlin sources need no encoding marker) and carry 18. Gradle enumerates its 18 structural rules in the `buildSrc` core engine and its 14 code-style rules via ktlint; Maven uses JavaParser for both surfaces. Shell implements a portable 8-rule structural subset and no code-style rules. The per-stack boundary is the same in every case: ecosystem linters (or the native AST engine) own code-style detection, leaving the harness engine focused on repository-wide structural invariants.

**Optional conventions (droppable on request):** The code-style rules are opinionated by design and enabled by default. `greaterThanComparison`, `unstructuredLogging`, `wildcardImport`, and `classMemberOrdering` encode strong stylistic conventions; a team whose conventions differ may disable any of them per repository via the manifest. They are kept enabled by default rather than removed, because a strict audit found they fire no false positives. `greaterThanComparison` is the prime candidate if conventions are trimmed further. Separately, the bun oxlint config (`.oxlintrc.json`) currently installs under `docs/harness/bun/` while the oxfmt config (`.oxfmtrc.json`) installs at the project root; aligning them is tracked as low-risk follow-up in `docs/specs/2026-06-03-bun-oxlint-oxfmt-uv-ruff-migration-design.md`.

AST/PSI validators use semantic tree traversal for code-structure rules rather than per-check CLI switches or regex-only scans. Inspired by the LY Tech Blog AST validation posture, formatting can remain a separate concern while structural validators compare declarations, ownership, and member order in the parsed tree, leaving room for before/after tree validation through the existing manifest-driven categories.

| Mode | Structural parser | Code-order coverage |
| --- | --- | --- |
| Gradle/Kotlin+Java | Kotlin PSI through the Gradle worker classloader plus JavaParser for Java sources | Kotlin class member ordering, Java class member ordering, companion-object position, top-level declaration shape, terminal Kotlin if/else-to-when enforcement, and Kotlin code-style checks. Kotlin enum entries and Java enum constants are treated as language-mandated enum preamble items, not ordinary sortable members. |
| Maven/Java | JavaParser inside the Maven plugin | Java class member ordering and Java code-style checks. Java enum constants are treated as language-mandated enum preamble items, not ordinary sortable members. Maven+Kotlin source validation is not currently enabled because the Maven adapter does not embed Kotlin PSI. |
| uv/Python | ruff (`F403` via ruff CLI) for wildcard-import detection; LibCST for the remaining 7 AST rules | Python code-style checks for the selected uv slice via ruff and LibCST. |
| Bun/TypeScript | oxlint (Oxc parser) for code-style; harness engine for structural, manifest, and filesystem rules | oxlint enforces 9 TypeScript code-style rules (6 built-in: no-console, no-empty (allowEmptyCatch:false), curly, no-underscore-dangle (allow:["_"]), no-namespace, ban-ts-comment; 3 custom oxlint JS-plugin rules: greaterThanComparison, multilineDocStyle, publicDeclarationDocComment) and oxfmt normalizes layout (indentation and blank-line collapsing). The harness engine retains 19 structural, manifest, and filesystem rules for the selected Bun slice. |
| Shell | POSIX shell plus `python3` for manifest reads | Hook shebang and executable validation, hook command parity, CI command parity, symlink/path safety for manifest-controlled filesystem paths, scaffold-leak scanning, completed-plan unchecked-task scanning, and shellcheck. |

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

| Rule category | Bun | uv | Gradle | Maven | Shell | Mutation type |
| --- | --- | --- | --- | --- | --- | --- |
| `greaterThanComparison` | Defer | Defer | Partial | Partial | Defer | Rewrite simple identifier/literal `>` and `>=` comparisons to `<` and `<=` with operands swapped; expressions that may affect evaluation order stay check-only |
| `leafFunctionBlankLines` | oxfmt | ruff format | Allow | Allow | Defer | Remove blank lines inside leaf function bodies |
| `emptyDirectoryPlaceholders` | Allow | Allow | Allow | Allow | Defer | Create `.gitkeep` in empty required directories |
| `envShebangUsage` | Allow | Allow | Allow | Allow | Allow | Replace script shebang with `/usr/bin/env` form |
| `hookGeneratedMarker` | Allow | Allow | Allow | Allow | Allow | Insert generated marker in managed hook template |
| `hookShebang` | Allow | Allow | Allow | Allow | Allow | Replace missing or incorrect hook shebang |
| `hookExecutable` | Allow | Allow | Allow | Allow | Allow | Set executable bit on configured hook scripts |
| `shebangEncodingMarker` | Allow | Allow | Defer | Defer | Defer | Insert encoding marker after shebang |

Rules not in this table are not formatted. `harnessFormat` is idempotent: a second run immediately after the first produces no additional modifications. Format commands MUST report changed files or a clear no-op summary, then run validation and print remaining findings; commands fail when any remaining finding has `ERROR` severity. For the bun stack, `leafFunctionBlankLines` is not a harness safe-fix; oxfmt performs blank-line normalization during `format` (oxfmt configuration is auto-discovered from `.oxfmtrc.json` at the project root). For the uv stack, `ruff format` applies full ruff formatting (quotes, line length, trailing commas, blank-line runs) to target `.py` sources under `parameters.sourceRoots`, not only blank-line normalization.

### Shell formatting

The shell runtime ships `harness-check.sh` and `harness-format.sh`. Shell validation covers file presence, directory structure, hook shebangs and executable bits, hook command parity, CI command parity, symlink/path safety for manifest-controlled filesystem paths, scaffold-leak scanning, completed-plan unchecked-task scanning, and shellcheck. Shell formatting runs `shfmt` across `.sh` files under the target root, then applies manifest-aware safe fixes (including executable and generated hook markers), and runs `harness-check.sh` returning the remaining validation status.

## Git Hooks

The installer writes two selected-mode hook templates in `docs/harness/git-hooks/` on fresh install. Gradle `pre-commit` runs `harnessCheck` for intermediate harness feedback, and Gradle `pre-push` runs `check` for the final push gate. Non-Gradle `pre-commit` performs lightweight harness-rule compliance checks, and non-Gradle `pre-push` runs the selected validation command. Managed generated templates refresh with the selected intermediate and final commands; custom target-owned templates are preserved unless `--force` is used. Git hook activation is opt-in because it modifies local Git behavior outside version control.

```sh
sh /path/to/sinon/plugins/harness/skills/harness-install/scripts/install-harness.sh --target /path/to/target-repo --mode uv --hooks copy
```

Use `--hooks none` or omit the flag to skip Git hook activation. Use `--hooks copy` only when the target should copy both generated hooks to `pre-commit` and `pre-push` in the active worktree hooks directory.

Use `--hooks build-tool` to print build-tool activation commands and run them only when the user chooses. This mode is only available for Gradle and Maven targets; uv, bun, and shell modes reject it with an error. No active hook installation occurs during installation.

### Build-tool hook activation (Gradle)

The Gradle asset `settings.gradle.kts` applies [`org.danilopianini.gradle-pre-commit-git-hooks`](https://github.com/DanySK/gradle-pre-commit-git-hooks) version `2.1.17` and configures `preCommit` from `docs/harness/git-hooks/pre-commit` and `pre-push` via `hook("pre-push")` from `docs/harness/git-hooks/pre-push` only when the Gradle property `harness.gitHooks=true` is set. Hooks are not created by default. When `harness.gitHooks.overwrite=true` is also set, existing hooks are overwritten via `createHooks(true)`; otherwise `createHooks()` preserves any existing hook files.

With `--hooks build-tool`, the installer prints the exact command to run manually (`./gradlew -Pharness.gitHooks=true help` or `gradle -Pharness.gitHooks=true help`) and does not run Gradle during install.

**Linked worktree limitation:** The `gradle-pre-commit-git-hooks` plugin resolves the Git directory relative to the project root. In linked Git worktrees, the plugin may place hooks in the wrong `.git` location. For linked worktree Gradle projects, prefer `--hooks copy` or manual `core.hooksPath` configuration.

### Build-tool hook activation (Maven)

The Maven asset `harness-maven-plugin/pom.xml` includes an opt-in profile `harness-git-hooks` activated by `-Dharness.gitHooks=true`. The profile uses [`com.rudikershaw.gitbuildhook:git-build-hook-maven-plugin`](https://github.com/rudikershaw/git-build-hook-maven-plugin) version `3.6.0` with the `configure` goal to set `core.hooksPath` to `docs/harness/git-hooks`. The `configure` goal is preferred over `install` because it points Git at the tracked hook directory without overwriting hook files. The profile is not active by default.

With `--hooks build-tool`, the installer prints `mvn -q -f harness-maven-plugin/pom.xml -Dharness.gitHooks=true generate-sources` for manual execution and does not run Maven during install.

If `core.hooksPath` already points to a directory other than `docs/harness/git-hooks`, the installer rejects `--hooks build-tool` to avoid stacking activation.

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
- The bun harness runtime under `docs/harness/bun/` is excluded from the target's `sourceRoots` and is not self-linted; harness self-checks exercise the bun rules through positive/negative fixture corpora rather than linting the shipped runtime.
- The uv harness runtime under `docs/harness/uv/` is excluded from the target's `sourceRoots` and is not self-linted by `ruff` or `ruff format`. Wildcard-import detection via `ruff` and the remaining 7 AST rules via LibCST apply only to installed-target sources. Enforcement of excess in-function blank lines moved from `check` (read-only CI reporting) to `format` (destructive edit); a check-only target will no longer see blank-line findings, only `format` collapses them.
- The Maven/Java stack intentionally stays on the JavaParser harness engine for detection. The only ecosystem Java tool in the stack is Spotless, configured format-only (`removeUnusedImports`/`trimTrailingWhitespace`/`endWithNewline`); the project deliberately avoids a full Java formatter, so `leafFunctionBlankLines` keeps its bespoke JavaParser `format()` rather than being subsumed the way `oxfmt`/`ruff format` subsume it on the bun/uv stacks. This is the correct end-state, not an unfinished migration. Adopting Checkstyle/PMD detection or a full formatter is tracked tech-debt requiring an explicit user decision; see `docs/specs/2026-06-03-bun-oxlint-oxfmt-uv-ruff-migration-design.md`.
