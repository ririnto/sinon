# Harness

Harness is a portable plugin for staged setup-time docs, configuration, and validator guardrails in target repositories.

## Purpose

- Inspect an existing repository and create or update `docs/harness-engineering/harness-engineering.json` as this plugin's visible, target-specific docs-system metadata convention.
- Use the OpenAI Harness Engineering article as knowledge-base posture, not as the source for the JSON path or filename: root `CLAUDE.md`, `AGENTS.md -> CLAUDE.md`, root `ARCHITECTURE.md`, and `docs/` preserve design docs, execution plans, generated docs, product specs, references, and topic docs.
- Keep Symphony-style orchestration facts out of the core product surface: this plugin can prepare repository knowledge, guardrails, and evidence paths, but it does not run a daemon, poll Linear, launch an app-server client, or manage per-issue workspaces.
- Install or update docs, scripts, CI templates, git hook templates, and harness engineering validation without assuming one framework or build system.
- Stage guardrails from discovery to advisory checks to shared `error` gates, then ratchet maturity with known-violation and doc-freshness controls.
- Keep implementation, repository docs, specs, ADR constraints, and deterministic checks evolving together so agents follow documented information instead of hidden convention.
- Provide bundled agent definitions that read the target repository's harness configuration and docs before enforcing architecture, reviewing changes, gardening docs, driving end-to-end work, or maintaining execution plans.

## Included Skill

- `setup-harness`: inspect, configure, install, validate, and update staged repository guardrails declared in `docs/harness-engineering/harness-engineering.json`.

## Included Agents

- `architecture-guard`: mechanical architecture auditing against the target repository's declared source roots, scoped path rules, layer model, checks, and docs.
- `code-reviewer`: evidence-backed review against the target harness configuration, documented principles, readiness gates, and configured validation commands.
- `doc-gardener`: report-only entropy cleanup using configured docs paths, freshness expectations, generated artifacts, readiness score, and link checks.
- `e2e-driver`: autonomous end-to-end execution that discovers the target repository's runtime, validation, browser automation, and evidence commands from harness docs.
- `spec-writer`: execution-plan authoring and lifecycle management under the configured plan paths.

## Runtime Model

This plugin is packaged for Claude Code from one shared plugin root:

- `.claude-plugin/plugin.json`

The manifest points to the shared plugin root content. The reusable surfaces live under `skills/` and `agents/` beside the manifest. Agents are intentionally not declared in the manifest because the plugin packaging schema rejects an `agents` key. Treat `agents/*.md` as bundled source definitions: use the host's plugin-agent discovery when available, or copy/adapt the needed agents into a target repository's `.claude/agents/` directory and document that activation decision in the harness docs.

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
plugins/harness/
├── .claude-plugin/plugin.json
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
    └── setup-harness/
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
        │   │                       └── setup-harness/
        │   │                           └── HarnessCheckMojo.java.template
        │   ├── bun-validator/
        │   │   └── validate-harness.mjs.template
        │   ├── proof-packet.md.template
        │   ├── spec.md.template
        │   └── workflow.md.template
        ├── scripts/
        │   ├── validate_harness.py
        │   └── validate_harness.sh
        └── references/
            ├── architecture-enforcement.md
            ├── bootstrap-apply-flow.md
            ├── ci-hooks-integration.md
            ├── docs-as-context.md
            ├── entropy-management.md
            ├── source-verification.md
            └── symphony-service-specification.md
```

- `.claude-plugin/plugin.json` carries thin Claude-facing marketplace metadata with the declared `skills` entry point and shared plugin metadata.
- `skills/setup-harness/SKILL.md` holds the common setup path: inspect, configure, install stage-appropriate docs/scripts/CI/hooks guardrails, validate gates, and update/ratchet.
- `skills/setup-harness/assets/` holds raw copy/adapt assets named with the target filename extension before `.template`, including root instruction docs, staged harness docs, execution/spec/workflow/proof-packet templates, CI and hook templates, plus optional Bun, Gradle, and Maven integration packs that run language-native harness validation.
- `skills/setup-harness/scripts/` holds the full Python validator and direct `sh` wrapper that may be copied into target repositories.
- `scripts/validate_plugin.py` validates only harness metadata required for publication: the Claude manifest contract, absence of stale removed package metadata, and agent/skill frontmatter.
- `skills/setup-harness/references/` holds additive depth for bootstrap/apply flow, architecture enforcement, CI/hooks integration, docs-as-context, entropy management, source verification, and the original Symphony service specification artifact.
- `agents/*.md` define autonomous agent source files with explicit triggers, bounded tools, and prompts that read target repository configuration before enforcing rules. Host packaging may expose them directly; when it does not, copy the selected agent files into the target repository's `.claude/agents/` directory instead of assuming manifest activation.

## Integration Packs

The common path installs docs, config, scripts, CI, and hooks only. These copy/adapt packs are available when a target repository wants an existing toolchain to validate the versioned harness contract natively:

| Pack | Asset path | Purpose |
| --- | --- | --- |
| Bun validator | `skills/setup-harness/assets/bun-validator/validate-harness.mjs.template` | Bun no-dependency validator for package scripts or Bun-based tooling |
| Gradle plugin | `skills/setup-harness/assets/gradle-plugin/` | Included build-logic convention plugin that registers `harnessCheck` with Gradle-native validation; `buildSrc` is an alternative target shape |
| Maven plugin | `skills/setup-harness/assets/maven-plugin/` | Maven Mojo that exposes a `harness-check` goal with Maven-native validation |

Do not treat these packs as always-on validator lanes. Adopt one only after the target repository documents the target files and command surface in its harness docs.

## Source Verification

The OpenAI Harness Engineering article was rechecked from `https://openai.com/ko-KR/index/harness-engineering/` on 2026-05-09 before this update. It informs the repository knowledge-base shape and enforcement posture, while this plugin adds the `docs/harness-engineering/harness-engineering.json` metadata convention, staged fields, validators, and harness checks. The related Symphony article and public spec were checked on 2026-05-09 only to define product boundaries. The encoded source mapping lives in `skills/setup-harness/references/source-verification.md`.

## Design Principles

- Treat `docs/harness-engineering/harness-engineering.json` as this plugin's stable docs-adjacent metadata convention: target-specific, machine-readable, namespaced, and separate from root `CLAUDE.md`, `AGENTS.md`, and `ARCHITECTURE.md`. Root `.harness-engineering.json` is manual migration input only, not a validator-supported fallback.
- Use root `CLAUDE.md` as the repository-level instruction map because Claude Code reads project `CLAUDE.md` files, and create `AGENTS.md -> CLAUDE.md` for AGENTS.md readers. Do not create `CLAUDE.md -> AGENTS.md`.
- Keep root `ARCHITECTURE.md` as the default architecture path. Do not default to `docs/ARCHITECTURE.md`.
- Keep the plugin a setup/update/validation skill, not an always-on universal operating skill.
- Install non-destructively: dry-run first, report conflicts, and never overwrite target files without an explicit replace decision.
- Keep `docs/` and `scripts/` evolving together so documented behavior is backed by executable checks.
- Run `scripts/validate_plugin.py` before shipping harness changes so Claude manifest drift, stale removed package metadata, and agent/skill metadata fail locally without broad filesystem scans.
- Support GitHub Actions, GitLab CI, git commit/push hooks, and user-customized requirement rules without requiring any one provider or build system.
- Use the shell wrapper as the trusted default command form. The wrapper executes Python only through `uv run`, so target repositories must provision `uv`.
- Use readiness/maturity scoring, `warn`-to-`error` ratchets, known-violation ledgers, ADR/spec constraints, doc freshness, and scoped path rules as practical config fields and gates.
- Keep source schemas in checked-in project roots, not `docs/generated/`. Use `docs/generated/` for reproducible derived docs with provenance, source paths, and regeneration commands.
- Keep project-specific build/runtime integrations outside the core validator. Use the optional Bun, Gradle, or Maven packs only when a target repository explicitly adopts that entrypoint.
- Keep Symphony daemon behavior, Linear issue tracking, per-issue workspace scheduling, app-server protocol handling, and OpenAI runtime-stack provisioning outside this plugin. Document those as separate target-specific orchestration integrations when needed.

## Installation

Install from the Sinon marketplace:

```bash
/plugin install harness@sinon
```

For Claude Code local development:

```bash
claude --plugin-dir /path/to/sinon/plugins/harness
```


## Plugin Validation

Run the plugin validator before shipping harness changes to check the Claude manifest constraints, stale removed package metadata, and agent/skill frontmatter metadata:

```bash
uv run scripts/validate_plugin.py
```

The validator intentionally has one narrow mode for harness engineering metadata. It is not a documentation inventory checker, config drift checker, or package filesystem linter.

## Scope Notes

This plugin intentionally focuses on applying and maintaining staged setup-time docs/config guardrails. It does not cover:

- language- or framework-specific coding style beyond configured checks
- mandatory project build-system lane management
- release management or deployment tooling
- general-purpose CI pipeline authoring beyond integrating configured harness checks
- Symphony daemon implementation, Linear polling, app-server clients, or per-issue workspace orchestration
- OpenAI runtime stack provisioning, observability backend provisioning, or browser automation service hosting
- destructive repository migrations or automatic overwrites

Target repositories SHOULD preserve `AGENTS.md -> CLAUDE.md` and `.agents/skills -> ../.claude/skills` when they use both naming surfaces. Target repositories that need project-specific agents MAY add `.claude/agents/` and document those agents in the harness docs.
