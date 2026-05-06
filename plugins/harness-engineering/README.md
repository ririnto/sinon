# Harness Engineering

Harness Engineering is a portable plugin for staged setup-time guardrails in target repositories.

## Purpose

- Inspect an existing repository and create or update `docs/harness-engineering/harness-engineering.json` as the visible target-specific source of truth.
- Default to the OpenAI article's harness structure while preserving dependency direction: root `CLAUDE.md`, `AGENTS.md -> CLAUDE.md`, root `ARCHITECTURE.md`, and `docs/` for design docs, execution plans, generated docs, product specs, references, and topic docs.
- Install or update docs, scripts, CI templates, git hook templates, optional Gradle and Node wiring, readiness scoring, customizable `warn`/`error` user requirement rules, and validation checks without assuming one framework or layer model.
- Stage guardrails from discovery to advisory checks to `error` CI, Gradle, Node, or standalone gates, then ratchet maturity with known-violation and doc-freshness controls.
- Support project profiles such as `generic` and `spring-boot` so source roots, schema sources, generated artifact policy, build integration, and layer enforcement details can differ by project type.
- Keep implementation, repository docs, specs, ADR constraints, and deterministic checks evolving together so agents follow documented information instead of hidden convention.
- Provide bundled agents that read the target repository's harness configuration and docs before enforcing architecture, reviewing changes, gardening docs, driving end-to-end work, or maintaining execution plans.

## Included Skill

- `harness-engineering`: inspect, configure, install, validate, and update staged repository guardrails declared in `docs/harness-engineering/harness-engineering.json`.

## Included Agents

- `architecture-guard`: mechanical architecture auditing against the target repository's declared source roots, scoped path rules, layer model, checks, and docs.
- `code-reviewer`: evidence-backed review against the target harness configuration, documented principles, readiness gates, and configured validation commands.
- `doc-gardener`: report-only entropy cleanup using configured docs paths, freshness expectations, generated artifacts, readiness score, and link checks.
- `e2e-driver`: autonomous end-to-end execution that discovers the target repository's runtime, validation, and evidence expectations from harness docs.
- `spec-writer`: execution-plan authoring and lifecycle management under the configured plan paths.

## Runtime Model

This plugin uses one shared plugin root with two thin runtime manifests:

- `.claude-plugin/plugin.json`
- `.codex-plugin/plugin.json`

Both manifests point to the same shared plugin root content. The reusable surfaces live under `skills/` and `agents/` beside those manifests. Agents are intentionally not declared in either manifest because the plugin packaging schema rejects an `agents` key; target repositories may still copy or adapt project-specific agents into `.claude/agents/` when needed.

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
├── .githooks/
│   ├── commit-msg
│   └── pre-push
├── build.gradle.kts
└── package.json
```

GitHub Actions, GitLab CI, git hooks, Gradle, Node package scripts, and standalone shell/script lanes are optional integration surfaces. Install only the surfaces declared by the target repository config.

## Staged Guardrail Model

| Stage | Purpose | Exit gate |
| --- | --- | --- |
| Stage 0 discovery | Inventory current docs, scripts, source roots, CI, hooks, build files, and known violations | Existing surfaces and conflicts are listed |
| Stage 1 visibility/docs/config | Put repository configuration under `docs/harness-engineering/` | Config, guardrails, readiness, updates, and entrypoint docs validate |
| Stage 2 advisory scripts/hooks | Add local `warn` checks and hooks | Validator runs locally and records known violations without failing legacy debt |
| Stage 3 error shared gates | Promote agreed checks to shared enforcement | Required commands pass in CI, Gradle `check`, Node package scripts, or standalone scripts, and `error` findings fail |
| Stage 4 ratchet/freshness/maturity hardening | Tighten readiness score, doc freshness, and known-violation budgets | New `error` findings fail and known violations trend down |

## Plugin Layout

```text
plugins/harness-engineering/
├── .claude-plugin/plugin.json
├── .codex-plugin/plugin.json
├── README.md
├── agents/
│   ├── architecture-guard.md
│   ├── code-reviewer.md
│   ├── doc-gardener.md
│   ├── e2e-driver.md
│   └── spec-writer.md
└── skills/
    └── harness-engineering/
        ├── SKILL.md
        ├── assets/
        │   ├── claude-md-template.md
        │   ├── config-template.json
        │   ├── docs-directory-scaffold.md
        │   ├── execution-plan-template.md
        │   ├── gitignore-template.md
        │   ├── git-hooks.md
        │   ├── github-actions.yml
        │   ├── gitlab-ci.yml
        │   ├── gradle-integration.gradle.kts
        │   ├── node-integration-package-json.md
        │   └── harness-docs-update.md
        ├── scripts/
        │   ├── validate_harness.sh
        │   └── validate_harness.py
        └── references/
            ├── agent-legibility.md
            ├── architecture-enforcement.md
            ├── bootstrap-apply-flow.md
            ├── ci-hooks-gradle-integration.md
            ├── docs-as-context.md
            ├── entropy-management.md
            └── source-verification.md
```

- `.claude-plugin/plugin.json` carries thin Claude-facing marketplace metadata with the declared `skills` entry point and shared plugin metadata.
- `.codex-plugin/plugin.json` carries thin Codex-facing marketplace metadata pointing at the same shared plugin root and the Codex interface block.
- `skills/harness-engineering/SKILL.md` holds the common setup path: inspect, configure, install stage-appropriate guardrails, validate gates, and update/ratchet.
- `skills/harness-engineering/assets/` holds copyable target-repository templates for visible config, docs, CI, hooks, Gradle, Node, and update records.
- `skills/harness-engineering/scripts/` holds the offline deterministic validator and direct `sh` wrapper that may be copied into target repositories.
- `skills/harness-engineering/references/` holds additive depth for bootstrap/apply flow, architecture enforcement, CI/hooks/build integration, docs-as-context, entropy management, and agent legibility.
- `agents/*.md` define autonomous agents with explicit triggers, bounded tools, and prompts that read target repository configuration before enforcing rules.

## Source Verification

The OpenAI article was rechecked from `https://openai.com/ko-KR/index/harness-engineering/` on 2026-05-07 before this refactor. The encoded source mapping lives in `skills/harness-engineering/references/source-verification.md`.

## Design Principles

- Treat `docs/harness-engineering/harness-engineering.json` as authoritative for the target repository. Root `.harness-engineering.json` is legacy fallback only.
- Use root `CLAUDE.md` as the repository-level instruction map for agent rules and create `AGENTS.md -> CLAUDE.md` for AGENTS.md readers. Do not create `CLAUDE.md -> AGENTS.md`.
- Keep root `ARCHITECTURE.md` as the default architecture path. Do not default to `docs/ARCHITECTURE.md`.
- Keep the plugin a setup/update/validation skill, not an always-on universal operating skill.
- Install non-destructively: dry-run first, report conflicts, and never overwrite target files without an explicit replace decision.
- Keep `docs/` and `scripts/` evolving together so documented behavior is backed by executable checks.
- Support GitHub Actions, GitLab CI, git commit/push hooks, optional Gradle `check` integration, Node package scripts, standalone script lanes, and user-customized requirement rules without requiring any one provider.
- Use readiness/maturity scoring, `warn`-to-`error` ratchets, known-violation ledgers, ADR/spec constraints, doc freshness, and scoped path rules as practical config fields and gates.
- Keep source schemas in checked-in project roots, not `docs/generated/`. Use `docs/generated/` for reproducible derived docs with provenance, source paths, and regeneration commands.
- Let project profiles change defaults: Spring Boot may use Gradle checks, Node frontends may use npm/pnpm/Yarn/Bun package scripts, standalone repos may use shell/Python/Make/Taskfile lanes, and all profiles may add user requirement rules with `warn` or `error` severity.

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

## Scope Notes

This plugin intentionally focuses on applying and maintaining staged setup-time guardrails. It does not cover:

- language- or framework-specific coding style beyond configured checks
- release management or deployment tooling
- general-purpose CI pipeline authoring beyond integrating configured harness checks
- destructive repository migrations or automatic overwrites

Target repositories SHOULD preserve `AGENTS.md -> CLAUDE.md` and `.agents/skills -> ../.claude/skills` when they use both naming surfaces. Target repositories that need project-specific agents MAY add `.claude/agents/` and document those agents in the harness docs.
