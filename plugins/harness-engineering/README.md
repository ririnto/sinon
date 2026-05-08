# Harness Engineering

Harness Engineering is a portable plugin for staged setup-time docs, configuration, and validator guardrails in target repositories.

## Purpose

- Inspect an existing repository and create or update `docs/harness-engineering/harness-engineering.json` as this plugin's visible, target-specific docs-system metadata convention.
- Use the OpenAI article as knowledge-base posture, not as the source for the JSON path or filename: root `CLAUDE.md`, `AGENTS.md -> CLAUDE.md`, root `ARCHITECTURE.md`, and `docs/` preserve design docs, execution plans, generated docs, product specs, references, and topic docs.
- Install or update docs, scripts, CI templates, git hook templates, and harness engineering validation without assuming one framework or build system.
- Stage guardrails from discovery to advisory checks to shared `error` gates, then ratchet maturity with known-violation and doc-freshness controls.
- Keep implementation, repository docs, specs, ADR constraints, and deterministic checks evolving together so agents follow documented information instead of hidden convention.
- Provide bundled agent definitions that read the target repository's harness configuration and docs before enforcing architecture, reviewing changes, gardening docs, driving end-to-end work, or maintaining execution plans.

## Included Skill

- `harness-engineering`: inspect, configure, install, validate, and update staged repository guardrails declared in `docs/harness-engineering/harness-engineering.json`.

## Included Agents

- `architecture-guard`: mechanical architecture auditing against the target repository's declared source roots, scoped path rules, layer model, checks, and docs.
- `code-reviewer`: evidence-backed review against the target harness configuration, documented principles, readiness gates, and configured validation commands.
- `doc-gardener`: report-only entropy cleanup using configured docs paths, freshness expectations, generated artifacts, readiness score, and link checks.
- `e2e-driver`: autonomous end-to-end execution that discovers the target repository's runtime, validation, browser automation, and evidence commands from harness docs.
- `spec-writer`: execution-plan authoring and lifecycle management under the configured plan paths.

## Runtime Model

This plugin uses one shared plugin root with two thin runtime manifests:

- `.claude-plugin/plugin.json`
- `.codex-plugin/plugin.json`

Both manifests point to the same shared plugin root content. The reusable surfaces live under `skills/` and `agents/` beside those manifests. Agents are intentionally not declared in either manifest because the plugin packaging schema rejects an `agents` key. Treat `agents/*.md` as bundled source definitions: use the host's plugin-agent discovery when available, or copy/adapt the needed agents into a target repository's `.claude/agents/` directory and document that activation decision in the harness docs.

## Target Repository Layout

The plugin creates or maintains this staged setup shape in a target repository, subject to config and no-overwrite conflict handling.

```text
target-repo/
├── CLAUDE.md
├── AGENTS.md -> CLAUDE.md
├── ARCHITECTURE.md
├── .claude/
│   ├── agents/
│   └── skills/
├── .agents/
│   └── skills -> ../.claude/skills
├── docs/
│   ├── harness-engineering/
│   │   ├── harness-engineering.json
│   │   ├── guardrails.md
│   │   ├── known-violations.md
│   │   ├── readiness.md
│   │   └── updates.md
│   ├── design-docs/
│   │   ├── index.md
│   │   └── core-beliefs.md
│   ├── exec-plans/
│   │   ├── active/
│   │   ├── completed/
│   │   └── tech-debt-tracker.md
│   ├── generated/
│   │   └── db-schema.md
│   ├── product-specs/
│   │   ├── index.md
│   │   └── new-user-onboarding.md
│   ├── references/
│   │   ├── design-system-reference-llms.txt
│   │   ├── nixpacks-llms.txt
│   │   └── uv-llms.txt
│   ├── DESIGN.md
│   ├── FRONTEND.md
│   ├── PLANS.md
│   ├── PRODUCT_SENSE.md
│   ├── QUALITY_SCORE.md
│   ├── RELIABILITY.md
│   └── SECURITY.md
├── scripts/
│   └── harness/
│       ├── validate_harness.py
│       └── validate_harness.sh
├── .github/workflows/harness-checks.yml
├── .gitlab-ci.yml
└── .githooks/
    ├── commit-msg
    └── pre-push
```

GitHub Actions, GitLab CI, and git hooks are optional integration surfaces. Project-specific build or runtime integration packs may layer additional surfaces separately, but they are not part of this core plugin scope.

## Staged Guardrail Model

Stages, hooks, required commands, and optional commands are declarative lifecycle gates. Keep the target config as the place that names each gate, its enforcement mode, and the command surface that satisfies it; do not hide lifecycle behavior in ad hoc CI or hook scripts.

| Stage | Purpose | Exit gate |
| --- | --- | --- |
| Stage 0 discovery | Inventory current docs, scripts, source roots, CI, hooks, and known violations | Existing surfaces and conflicts are listed |
| Stage 1 visibility/docs/config | Put repository configuration under `docs/harness-engineering/` | Config, guardrails, readiness, updates, and entrypoint docs validate |
| Stage 2 advisory scripts/hooks | Add local `warn` checks and hooks | Validator runs locally and records known violations without failing legacy debt |
| Stage 3 error shared gates | Promote agreed checks to shared enforcement | Required commands pass through the shell wrapper in CI or hooks, and `error` findings fail |
| Stage 4 ratchet/freshness/maturity hardening | Tighten readiness score, doc freshness, and known-violation budgets | New `error` findings fail and known violations trend down |

## Plugin Layout

```text
plugins/harness-engineering/
├── .claude-plugin/plugin.json
├── .codex-plugin/plugin.json
├── .gitignore
├── README.md
├── agents/
│   ├── architecture-guard.md
│   ├── code-reviewer.md
│   ├── doc-gardener.md
│   ├── e2e-driver.md
│   └── spec-writer.md
├── scripts/
│   └── validate_plugin.py
└── skills/
    └── harness-engineering/
        ├── SKILL.md
        ├── assets/
        │   ├── CLAUDE.md.template
        │   ├── config.json.template
        │   ├── docs-directory-scaffold.md.template
        │   ├── execution-plan.md.template
        │   ├── git-hooks.md.template
        │   ├── github-actions.yml.template
        │   ├── gitignore.md.template
        │   ├── gitlab-ci.yml.template
        │   ├── gradle-plugin/
        │   │   ├── HarnessCheckTask.kt.template
        │   │   ├── HarnessConventionPlugin.kt.template
        │   │   └── build.gradle.kts.template
        │   ├── harness-docs-update.md.template
        │   ├── maven-plugin/
        │   │   ├── pom.xml.template
        │   │   └── src/
        │   │       └── main/
        │   │           └── java/
        │   │               └── com/
        │   │                   └── example/
        │   │                       └── harness/
        │   │                           └── HarnessCheckMojo.java.template
        │   └── node-validator/
        │       └── validate-harness.mjs.template
        ├── scripts/
        │   ├── validate_harness.py
        │   └── validate_harness.sh
        └── references/
            ├── architecture-enforcement.md
            ├── bootstrap-apply-flow.md
            ├── ci-hooks-integration.md
            ├── docs-as-context.md
            ├── entropy-management.md
            └── source-verification.md
```

- `.claude-plugin/plugin.json` carries thin Claude-facing marketplace metadata with the declared `skills` entry point and shared plugin metadata.
- `.codex-plugin/plugin.json` carries thin Codex-facing marketplace metadata pointing at the same shared plugin root and the Codex interface block.
- `skills/harness-engineering/SKILL.md` holds the common setup path: inspect, configure, install stage-appropriate docs/scripts/CI/hooks guardrails, validate gates, and update/ratchet.
- `skills/harness-engineering/assets/` holds raw copy/adapt assets named with the target filename extension before `.template`, plus optional Node, Gradle, and Maven integration packs that run language-native harness validation.
- `skills/harness-engineering/scripts/` holds the full Python validator and direct `sh` wrapper that may be copied into target repositories.
- `scripts/validate_plugin.py` validates only harness engineering metadata required for publication: paired manifests, shared manifest fields, runtime-specific manifest rules, and agent/skill frontmatter.
- `skills/harness-engineering/references/` holds additive depth for bootstrap/apply flow, architecture enforcement, CI/hooks integration, docs-as-context, and entropy management.
- `agents/*.md` define autonomous agent source files with explicit triggers, bounded tools, and prompts that read target repository configuration before enforcing rules. Host packaging may expose them directly; when it does not, copy the selected agent files into the target repository's `.claude/agents/` directory instead of assuming manifest activation.

## Integration Packs

The common path installs docs, config, scripts, CI, and hooks only. These copy/adapt packs are available when a target repository wants an existing toolchain to validate the same six-field harness contract natively:

| Pack | Asset path | Purpose |
| --- | --- | --- |
| Node validator | `skills/harness-engineering/assets/node-validator/validate-harness.mjs.template` | Node >=18 no-dependency validator for package scripts or Node-based tooling |
| Gradle plugin | `skills/harness-engineering/assets/gradle-plugin/` | Included build-logic convention plugin that registers `harnessCheck` with Gradle-native validation; `buildSrc` is an alternative target shape |
| Maven plugin | `skills/harness-engineering/assets/maven-plugin/` | Maven Mojo that exposes a `harness-check` goal with Maven-native validation |

Do not treat these packs as always-on validator lanes. Adopt one only after the target repository documents the target files and command surface in its harness docs.

## Source Verification

The OpenAI article was rechecked from `https://openai.com/ko-KR/index/harness-engineering/` on 2026-05-07 before this refactor. It informs the repository knowledge-base shape and enforcement posture, while this plugin adds the `docs/harness-engineering/harness-engineering.json` metadata convention, staged fields, validators, and harness engineering checks. The encoded source mapping lives in `skills/harness-engineering/references/source-verification.md`.

## Design Principles

- Treat `docs/harness-engineering/harness-engineering.json` as this plugin's stable docs-adjacent metadata convention: target-specific, machine-readable, namespaced, and separate from root `CLAUDE.md`, `AGENTS.md`, and `ARCHITECTURE.md`. Root `.harness-engineering.json` is manual migration input only, not a validator-supported fallback.
- Use root `CLAUDE.md` as the repository-level instruction map for agent rules and create `AGENTS.md -> CLAUDE.md` for AGENTS.md readers. Do not create `CLAUDE.md -> AGENTS.md`.
- Keep root `ARCHITECTURE.md` as the default architecture path. Do not default to `docs/ARCHITECTURE.md`.
- Keep the plugin a setup/update/validation skill, not an always-on universal operating skill.
- Install non-destructively: dry-run first, report conflicts, and never overwrite target files without an explicit replace decision.
- Keep `docs/` and `scripts/` evolving together so documented behavior is backed by executable checks.
- Run `scripts/validate_plugin.py` before shipping harness changes so manifest alignment and agent/skill metadata fail locally without broad filesystem scans.
- Support GitHub Actions, GitLab CI, git commit/push hooks, and user-customized requirement rules without requiring any one provider or build system.
- Prefer the shell wrapper as the only trusted default command form. Keep `uvx --offline --no-python-downloads python` as a fallback for the wrapper rather than a universal CI prerequisite.
- Use readiness/maturity scoring, `warn`-to-`error` ratchets, known-violation ledgers, ADR/spec constraints, doc freshness, and scoped path rules as practical config fields and gates.
- Keep source schemas in checked-in project roots, not `docs/generated/`. Use `docs/generated/` for reproducible derived docs with provenance, source paths, and regeneration commands.
- Keep project-specific build/runtime integrations outside the core validator. Use the optional Node, Gradle, or Maven packs only when a target repository explicitly adopts that entrypoint.

## Installation

Install from the Sinon marketplace:

```bash
/plugin install harness-engineering@sinon
```

For Claude Code local development:

```bash
claude --plugin-dir /path/to/sinon/plugins/harness-engineering
```

Codex-facing marketplace metadata ships through `.codex-plugin/plugin.json`, and it refers to the same plugin root content at this path.

## Plugin Validation

Run the plugin validator before shipping harness changes to check manifest alignment, runtime manifest constraints, and agent/skill frontmatter metadata:

```bash
python3 scripts/validate_plugin.py
```

The validator intentionally has one narrow mode for harness engineering metadata. It is not a documentation inventory checker, config drift checker, or package filesystem linter.

## Scope Notes

This plugin intentionally focuses on applying and maintaining staged setup-time docs/config guardrails. It does not cover:

- language- or framework-specific coding style beyond configured checks
- mandatory project build-system lane management
- release management or deployment tooling
- general-purpose CI pipeline authoring beyond integrating configured harness checks
- destructive repository migrations or automatic overwrites

Target repositories SHOULD preserve `AGENTS.md -> CLAUDE.md` and `.agents/skills -> ../.claude/skills` when they use both naming surfaces. Target repositories that need project-specific agents MAY add `.claude/agents/` and document those agents in the harness docs.
