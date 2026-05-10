---
name: setup-harness
description: >-
  Install, update, ratchet, and validate staged repository guardrails for target repositories. Use this skill when setting up, auditing, or evolving docs-adjacent harness metadata, CLAUDE.md/AGENTS.md/ARCHITECTURE/docs scaffolding, CI and hook templates, and harness checks from docs/harness/config.json.
compatibility: >-
  Ordinary authoring is offline. Target repositories that copy and run the bundled validator wrapper must provision `uv`; Python execution uses `uv run` only.
---

# Setup Harness

Install, update, ratchet, and validate staged repository guardrails that make a target repository visible, agent-legible, and mechanically checkable through docs-adjacent configuration, root instruction docs, a Python harness validator, CI templates, and git hook templates.

## Operating rules

- Treat `docs/harness/config.json` as this plugin's stable docs-adjacent metadata convention and source of truth for initial setup and later updates.
- Mention root `.config.json` only as manual migration input. Do not use it for new setup or describe it as a validator-supported fallback.
- Use staged adoption: document first, advise locally, then enforce in shared CI or hook gates only after readiness is visible.
- Keep the core config contract as the versioned machine-readable metadata in `assets/config.json`; update validator expectations and documentation together when the contract changes.
- Convert fixed architecture assumptions into docs or project-specific metadata outside the canonical config scaffold.
- Keep ordinary use offline. Do not require live docs, registries, hosted validators, network downloads, or generated build-system plugins to install or validate guardrails.
- Install non-destructively: dry-run first, report conflicts, and do not overwrite existing target files unless the caller explicitly asks for replacement.
- Target-owned files may diverge from these scaffolds: target repositories MAY add, modify, rename, or delete copied harness config, docs, scripts, CI, hooks, and agent files to fit their architecture and workflow.
- Use `assets/templates/` for copy-ready target documents and `assets/agents/` for copy-ready target agent definitions. Keep docs and scripts evolving together. A documented rule needs a matching validator check, CI/hook command, or explicit reason it is informational only.
- Keep directly run Python scripts on the `uv run` path with PEP 723 metadata before imports. Use PEP 508 dependency strings for external packages, and use `dependencies = []` for stdlib-only direct scripts when an explicit declaration is needed. Shell scripts have no equivalent metadata block, so document required commands beside the wrapper command or in this file.
- Treat stages, hooks, required commands, and optional commands as declarative lifecycle gates. Name the gate, enforcement mode, and command surface in config before wiring it into CI or hooks.
- Do not create parallel `author` and `writer` agents for the same job. Use one clear lifecycle role name and one responsibility per target agent.
- Treat `.claude/agents/*.md` as target-owned Claude agents. Use `paths.agentsRoot` to declare that repository surface, and do not describe those files as plugin manifest configuration.
- Use the verified OpenAI article for knowledge-base shape and enforcement posture, while keeping `docs/harness/config.json` as this plugin's own config convention. Preserve this repository's dependency direction: root `CLAUDE.md` is the repository-level rule map, `optional AGENTS.md -> CLAUDE.md` exposes the same map to AGENTS.md readers, root `ARCHITECTURE.md` stays outside `docs/`, and `docs/` holds deeper design docs, execution plans, generated docs, product specs, references, and top-level topic docs. Open `references/source-verification.md` when updating the source mapping.
- Keep `.agents/skills -> ../.claude/skills` when both skill surfaces exist.
- Keep Bun, Gradle, and Maven wiring as optional integration packs under `assets/`; do not add them to the core config validator or required command list unless the target repository explicitly adopts a pack.
- Keep Symphony-style orchestration outside this skill's setup-time product boundary. Do not implement or require a daemon, Linear issue polling, app-server client, per-issue workspace scheduler, or OpenAI runtime stack as part of ordinary harness setup.

## Target repository layout

The plugin creates or maintains this shape, adjusted by config and existing files.

```text
target-repo/
├── CLAUDE.md
├── optional AGENTS.md -> CLAUDE.md
├── ARCHITECTURE.md
├── .claude/
│   ├── agents/
│   │   └── {agent-name}.md  (target-owned, copied from assets/agents/{agent-name}.md or adapted for the target runtime)
│   └── skills/
├── .agents/
│   └── skills -> ../.claude/skills
├── docs/
│   ├── harness/
│   │   ├── config.json
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
│   │   └── db-schema.md  (optional generated artifact example)
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
│       ├── validate_harness.sh
│       └── setup-hooks.sh
├── .github/
│   └── workflows/
│       └── harness-checks.yml
├── .gitlab-ci.yml
└── .git/hooks/
    └── pre-commit  (generated by setup-hooks.sh)
```

Install only the docs, target agents, scripts, CI, and hook files the target repository declares. Project-specific build or runtime wiring may be layered through the optional Bun, Gradle, and Maven integration packs under `assets/`, but those packs are not core scope and are not installed by default.

## Staged setup model

Use stages to keep adoption safe.

1. Stage 1: create config, root instruction docs, architecture docs, harness docs, known-violation ledger, readiness docs, and local validator scripts.
2. Stage 2: add advisory git hooks or local checks that call the harness validator without blocking unrelated work.
3. Stage 3: promote selected checks to CI or hook `error` gates only after readiness and known-violation budgets support enforcement.

## Ownership boundary

This skill owns setup-time repository visibility and guardrail wiring:

- root `CLAUDE.md` as the canonical instruction map for Claude Code
- `optional AGENTS.md -> CLAUDE.md` as the compatibility surface for AGENTS.md readers
- root `ARCHITECTURE.md` as the default human architecture map
- `docs/harness/config.json` as the plugin-owned machine contract
- staged docs, validator scripts, CI snippets, hook templates, optional language-native integration packs, and target-owned `.claude/agents/` scaffolds

This skill does not own runtime orchestration. If a target repository wants Symphony-style operation, document it as a separate product or target-specific integration that owns daemon lifecycle, issue tracker credentials, per-issue workspaces, app-server protocol compatibility, runtime observability services, and ticket/PR mutation rules.

## Target agents

Target repositories that want Claude Code sub-agents must create their own `.claude/agents/*.md` files. These are target-owned configuration, not plugin configuration. Use `assets/agents/*.md` to scaffold each target agent: copy the asset, adapt it to the repository, and commit the result under `.claude/agents/`. The target-ready roles in `assets/agents/` cover harness lifecycle responsibilities rather than one agent per document. Use `agent-pool-curator` when multiple target agent files must change together, `implementation-drift-auditor` when docs must be checked against actual implementation, `spec-writer` for plans/specs, `doc-gardener` for docs-only entropy, and operational agents for config, CI/hooks, architecture, review, runtime evidence, and ratcheting. Installation is handled by this skill and `scripts/install_harness.py`, not by a copied target agent.

## Config template

Copy `assets/config.json` when starting from scratch, or diff it against the existing config during updates. Update config before copying docs, target agents, scripts, CI, or hooks so every installed artifact has a declared path, command surface, gate, evidence path, or absence policy.

The template is the versioned source of truth. Keep this section as a shape summary, not a duplicate full template.

```json
{
  "contractVersion": 1,
  "profile": {},
  "paths": {},
  "docs": {},
  "commands": {},
  "gates": {},
  "evidence": {},
  "packs": {},
  "absence": {}
}
```

## Procedure

### 1. Inspect the target repository

1. Read existing `CLAUDE.md`, `AGENTS.md`, `ARCHITECTURE.md`, `docs/`, CI files, hooks, and harness scripts if they exist.
2. Check whether `docs/harness/config.json` already exists.
3. Treat root `.config.json` as manual migration input only.
4. Identify configured docs, scripts, CI, hooks, validation commands, and pending install conflicts.
5. Record conflicts before writing files.
6. Choose the operating path: new install when no harness config exists, update or ratchet when config exists but docs/scripts/gates changed, or report-only audit when the request is status, drift, or maintenance review.

### 2. Create or update config

1. Start from `assets/config.json`; do not recreate the template from memory.
2. Preserve `contractVersion` and the current top-level groups: `profile`, `paths`, `docs`, `commands`, `gates`, `evidence`, `packs`, and `absence`.
3. Keep `commands.required` limited to `sh scripts/harness/validate_harness.sh` unless a separate integration pack explicitly owns additional trusted commands.
4. Keep CI provider, hook paths, pack paths, `paths.agentsRoot`, and absence policy declared before installing templates.
5. Put optional project-specific metadata such as source roots, layer models, generated-doc policy, or custom requirement rules outside the canonical template unless the target repository explicitly owns that extension.
6. If the target repository needs repository Claude agents, keep them under `.claude/agents/*.md`, copy or adapt from `assets/agents/*.md`, and set `absence.targetAgents` to `optional` or `required`.
7. If the target repository needs a multi-agent operating model, open `references/team-harness-patterns.md` and record the selected pattern in workflow docs, execution plans, or `docs/harness/guardrails.md` rather than expanding the canonical config scaffold.

### 3. Install stage-appropriate files

Perform a dry-run inventory first.

1. Map each asset to its configured target path.
2. For each target path, classify it as `create`, `update`, `conflict`, or `skip`.
3. Create missing directories only under configured docs, scripts, CI, hooks, and target agent roots.
4. Scaffold root `CLAUDE.md` from `assets/templates/CLAUDE.md` as a short map that points to `docs/harness/config.json`, root `ARCHITECTURE.md`, and configured docs paths.
5. Create `optional AGENTS.md -> CLAUDE.md` when no separate `AGENTS.md` already exists. Do not create `CLAUDE.md -> AGENTS.md` because that reverses the dependency direction. If an existing detailed `AGENTS.md` cannot be replaced, keep `absence.agentContextAlias` from claiming the alias is satisfied and record the migration decision in `docs/harness/updates.md`.
6. Create `.agents/skills` as a symlink to `../.claude/skills` when both skill inventories are used by the target repo.
7. Copy or adapt docs scaffolds from `assets/templates/`, especially `assets/templates/docs/harness/*.md` for guardrails, readiness, updates, and known violations.
8. Install `scripts/harness/validate_harness.py` and `scripts/harness/validate_harness.sh`.
9. Run the wrapper with `sh scripts/harness/validate_harness.sh`; the wrapper executes `uv run` directly.
10. Install CI from `assets/github-actions.yml` or `assets/gitlab-ci.yml` only when the stage promotes checks to CI.
11. Install hooks with `assets/setup-hooks.sh` only after reading `references/git-hooks.md` and documenting `sh scripts/harness/setup-hooks.sh`.
12. When `absence.targetAgents` is `optional` or `required`, create `paths.agentsRoot` and scaffold `.claude/agents/*.md` from `assets/agents/*.md`. Each agent file is target-owned: copy the asset, adapt role-specific scope, and commit under `.claude/agents/`. Do not symlink agent files back to the plugin source tree.
13. When a workflow doc is adopted, record operating model, roles, handoffs, and evidence paths in `docs/harness/guardrails.md`, `docs/PLANS.md`, or a target-owned execution plan.

### 4. Validate gates

Run the configured validator and every safe command listed under `commands.required`.

```bash
sh scripts/harness/validate_harness.sh
```

Validation must confirm:

- `docs/harness/config.json` is valid JSON, declares `contractVersion`, and keeps top-level groups aligned with `assets/config.json`.
- `ARCHITECTURE.md` is root-level by default; `docs/ARCHITECTURE.md` is not the OpenAI-profile default.
- `CLAUDE.md` is the default repository-level instruction map and is not an encyclopedia; `AGENTS.md` points to it.
- Harness docs exist: `guardrails.md`, `known-violations.md`, `readiness.md`, and `updates.md`.
- Declared docs and scripts paths exist or are reported as pending install actions.
- `AGENTS.md` is a symlink to `CLAUDE.md` when `absence.agentContextAlias` is `required`; pending conflicts are explicitly recorded instead of overwritten.
- Configured validation commands in `commands.required` and `commands.optional` must match trusted harness command forms and must not contain shell metacharacters.
- `.agents/skills` is a symlink to `../.claude/skills` when present.
- `.claude/agents/*.md` files exist when target-owned agents are declared, each with valid frontmatter containing exactly `name`, `description`, and `model`.
- CI points to the configured provider and calls the configured harness validator when Stage 3 is enabled.
- Git hooks call configured advisory checks without requiring network access when Stage 2 is enabled.
- Full specs, ADR constraints, architecture docs, scripts, and implementation paths are consistent enough for an agent to follow repository documentation before changing code.

### 5. Update and ratchet

Run the update path whenever docs, scripts, CI provider, hooks, guardrails, or validation commands change.

1. Read `docs/harness/config.json` and compare it with the current repository.
2. Update config first, then harness docs, then scripts, then CI and hooks.
3. Record meaningful changes in `docs/harness/updates.md` or the configured execution plan.
4. Re-run validation and fix drift introduced by the update.
5. Move checks from `warn` to `error` only when readiness and known-violation budgets satisfy the stage exit gate.
6. Report remaining conflicts instead of forcing overwrites.

## Optional Integration Packs

Use these packs only when the target repository explicitly wants a project-tooling entrypoint for the same versioned harness contract. Do not add their commands to `commands.required` or `commands.optional` by default; the target should enable only command forms supported by its CI toolchain.

| Pack | Asset path | Target shape | Command surface |
| --- | --- | --- | --- |
| Bun validator | `assets/bun-validator/validate-harness.mjs` | `scripts/harness/validate-harness.mjs` or package-script entrypoint | `bun scripts/harness/validate-harness.mjs` runs Bun-native harness config validation |
| Gradle plugin | `assets/gradle-plugin/` | included build-logic convention plugin by default; `buildSrc` is an alternative | `./gradlew harnessCheck` runs Gradle-native harness config validation |
| Maven plugin | `assets/maven-plugin/` | local Maven plugin module | `mvn local.harness:harness-maven-plugin:harness-check` runs Maven-native harness config validation |

Keep pack documentation beside the adopted target files. If a pack becomes shared enforcement, record the exact command and owner in the target harness docs before enabling error gates.

## Edge cases

- If a target repo still has root `.config.json`, treat it as unsupported manual migration input, move the durable values into `docs/harness/config.json`, and leave a short migration note rather than preserving two config conventions.
- If the target repo already has a detailed `AGENTS.md`, do not replace it. Report the alias conflict, leave `absence.agentContextAlias` unsatisfied, and propose migrating durable guidance into root `CLAUDE.md` plus configured deeper docs before creating `optional AGENTS.md -> CLAUDE.md`.
- If the target repo has no clear architecture model, document the provisional assumption in root `ARCHITECTURE.md` and `docs/harness/guardrails.md` rather than adding it to the canonical config scaffold.
- If source schemas live under language-specific paths, keep that as optional project-specific metadata outside the canonical config scaffold.
- If a check cannot be run offline, remove it from ordinary guardrails and document it as optional project-specific follow-up.
- If a repository needs build-tool-specific guardrails, use the Bun, Gradle, or Maven integration pack only after documenting the adopted files, trusted command form, and validation ownership.
- If a repository needs an agent-team operating model, keep this setup skill focused on docs and validation surfaces. Use the team-pattern reference to document target-specific roles, handoffs, and review gates before creating or changing agents.
- If a repository asks for Symphony, Linear, app-server, or long-running agent orchestration, keep the harness config focused on docs and validation surfaces. Record orchestration requirements in target-specific docs or an execution plan instead of expanding this skill's core contract.

## Output contract

Return:

1. The target repository config path and final `docs/harness/config.json` summary
2. The current staged guardrail phase and exit gate status
3. Created, updated, skipped, and conflicted files
4. Installed or updated docs, scripts, CI, hooks, and validation command status
5. Validation commands run and their results
6. Remaining risks, assumptions, known violations, or no-overwrite decisions
7. Recommended next step: audit, team-pattern workflow documentation, advisory checks, shared gates, or ratchet advancement

## Gotchas

- Do not impose the default `types -> config -> repo -> service -> runtime -> ui` model when the target repo declares a different model.
- Do not copy CI for both providers unless the target repo intentionally runs both.
- Do not make the root instruction entrypoint an encyclopedia. Repository-level rules start in `CLAUDE.md`, deeper rules live in configured docs paths, and `AGENTS.md` points to it.
- Do not store source schemas in `docs/generated/`; that directory is for reproducible derived documentation.
- Do not let docs describe checks that no script, CI job, hook, or explicit manual process can verify.
- Do not hard-code project-specific requirement rules into generic defaults. Keep them in target-owned docs or metadata outside the canonical template.
- Do not auto-run commands newly introduced or modified by the current change until a reviewer confirms they match the validator-trusted harness validator form and do not contain shell metacharacters.
- Do not advance from advisory to shared error gates until the target repository has documented the stage exit gate and known-violation posture.
- Do not require network access for ordinary validation.
- Do not hide command output with shell redirects; use visible output or quiet tool flags instead.

## Support files

- `assets/config.json` - copy or diff when creating or updating `docs/harness/config.json`
- `assets/templates/CLAUDE.md` - copy or diff when creating or updating the root `CLAUDE.md` map
- `assets/templates/ARCHITECTURE.md` and `assets/templates/docs/` - copy or diff when scaffolding configured docs and harness docs paths
- `assets/templates/docs/exec-plans/tech-debt-tracker.md` - copy or adapt when the target repo needs a configured execution-plan tracker
- `assets/templates/docs/product-specs/` and `assets/templates/docs/design-docs/` - copy when creating durable product or design specs
- `assets/templates/docs/harness/guardrails.md` - adapt when documenting target-specific workflow policy outside the core config contract
- `assets/templates/docs/harness/readiness.md` and `assets/templates/docs/harness/known-violations.md` - adapt when a change needs portable evidence for validation, review, or handoff
- `assets/templates/docs/harness/updates.md` - copy when recording harness updates, readiness changes, and docs/script alignment
- `assets/github-actions.yml` - copy or diff when `gates.ci.provider` is `github-actions` and Stage 3 error gates are enabled
- `assets/gitlab-ci.yml` - copy or diff when `gates.ci.provider` is `gitlab-ci` and Stage 3 error gates are enabled
- `references/git-hooks.md` - open when installing advisory commit and push hook templates
- `assets/templates/docs/.gitignore` - copy entries into `docs/.gitignore` when installing harness-generated docs or local generated caches
- `scripts/validate_harness.py` and `scripts/validate_harness.sh` - copy into target `scripts/harness/` when an offline validator and direct wrapper are needed
- `assets/bun-validator/validate-harness.mjs` - optional Bun no-dependency validator for repositories that expose harness validation through Bun tooling
- `assets/gradle-plugin/` - optional Gradle convention plugin pack that registers `harnessCheck` for Gradle-native config validation
- `assets/maven-plugin/` - optional Maven plugin pack that registers a `harness-check` goal for Maven-native config validation
- `references/bootstrap-apply-flow.md` - open when the target repository is empty or the apply/update sequence needs a deeper conflict plan
- `references/architecture-enforcement.md` - open when translating the configured layer model, scoped path rules, ADRs, or specs into deterministic checks
- `references/ci-hooks-integration.md` - open when wiring GitHub Actions, GitLab CI, git hooks, or optional Bun, Gradle, and Maven integration packs together
- `references/docs-as-context.md` - open when docs, full specs, ADRs, scripts, and implementation paths drift apart
- `references/entropy-management.md` - open when configuring readiness scoring, ratchets, doc freshness, or known-violation cleanup
- `references/team-harness-patterns.md` - open when adapting agent-team patterns, handoffs, and QA loops from `revfactory/harness` into target-owned docs
- `references/openai-harness-engineering.md` - open when adapting OpenAI Harness Engineering methodology into target docs, validation, or agent-readiness posture
- `references/openai-symphony.md` - open when a target repository asks about Symphony-style orchestration boundaries or readiness
- `references/source-verification.md` - open when updating or reviewing external source mappings
- `references/symphony-service-specification.md` - open only when comparing target-specific orchestration docs against the original Symphony service specification artifact
