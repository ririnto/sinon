---
name: harness-engineering
description: >-
  Apply staged setup-time docs and configuration guardrails for target repositories. Use this skill when installing, updating, ratcheting, or validating docs-adjacent harness metadata, CLAUDE.md/AGENTS.md/ARCHITECTURE/docs scaffolding, CI and hook templates, and plugin harness engineering checks from docs/harness-engineering/harness-engineering.json.
compatibility: >-
  Ordinary authoring is offline. Target repositories that copy and run the bundled validator wrapper should use their existing local Python when possible; the wrapper treats `uvx --offline --no-python-downloads python` as a fallback only when no local Python command is available.
---

# Harness Engineering

Install, update, ratchet, and validate staged repository guardrails that make a target repository visible, agent-legible, and mechanically checkable through docs-adjacent configuration, root instruction docs, a Python validator with harness validator, CI templates, and git hook templates.

## Operating rules

- Treat `docs/harness-engineering/harness-engineering.json` as this plugin's stable docs-adjacent metadata convention and source of truth for initial setup and later updates.
- Mention root `.harness-engineering.json` only as manual migration input. Do not use it for new setup or describe it as a validator-supported fallback.
- Use staged adoption: document first, advise locally, then enforce in shared CI or hook gates only after readiness is visible.
- Keep the core config contract to exactly six top-level fields: `stage`, `docs`, `scripts`, `checks`, `ci`, and `hooks`.
- Convert fixed architecture assumptions into docs or project-specific metadata outside the canonical config template.
- Keep ordinary use offline. Do not require live docs, registries, hosted validators, network downloads, or generated build-system plugins to install or validate guardrails.
- Install non-destructively: dry-run first, report conflicts, and do not overwrite existing target files unless the caller explicitly asks for replacement.
- Keep docs and scripts evolving together. A documented rule needs a matching validator check, CI/hook command, or explicit reason it is informational only.
- Treat stages, hooks, required commands, and optional commands as declarative lifecycle gates. Name the gate, enforcement mode, and command surface in config before wiring it into CI or hooks.
- Use the verified OpenAI article for knowledge-base shape and enforcement posture, while keeping `docs/harness-engineering/harness-engineering.json` as this plugin's own config convention. Preserve this repository's dependency direction: root `CLAUDE.md` is the repository-level rule map, `AGENTS.md -> CLAUDE.md` exposes the same map to AGENTS.md readers, root `ARCHITECTURE.md` stays outside `docs/`, and `docs/` holds deeper design docs, execution plans, generated docs, product specs, references, and top-level topic docs. Open `references/source-verification.md` when updating the source mapping.
- Keep `.agents/skills -> ../.claude/skills` when both skill surfaces exist.
- Keep Node, Gradle, and Maven wiring as optional integration packs under `assets/`; do not add them to the core config validator or required command list unless the target repository explicitly adopts a pack.

## Target repository layout

The plugin creates or maintains this shape, adjusted by config and existing files.

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
├── .github/
│   └── workflows/
│       └── harness-checks.yml
├── .gitlab-ci.yml
└── .githooks/
    ├── commit-msg
    └── pre-push
```

Install only the docs, scripts, CI, and hook files the target repository declares. Project-specific build or runtime wiring may be layered through the optional Node, Gradle, and Maven integration packs under `assets/`, but those packs are not core scope and are not installed by default.

## Staged setup model

Use stages to keep adoption safe.

1. Stage 1: create config, root instruction docs, architecture docs, harness docs, known-violation ledger, readiness docs, and local validator scripts.
2. Stage 2: add advisory git hooks or local checks that call the harness validator without blocking unrelated work.
3. Stage 3: promote selected checks to CI or hook `error` gates only after readiness and known-violation budgets support enforcement.

## Config template

Copy `assets/config.json.template` when starting from scratch, or diff it against the existing config during updates. Update config before copying docs, scripts, CI, or hooks so every installed artifact has a declared path and severity level.

```json
{
  "stage": {
    "current": 1,
    "target": 3,
    "mode": "advisory",
    "exitGate": "Config, docs, validator, and known-violation ledger exist before shared gates fail on error-level findings."
  },
  "docs": {
    "harnessRoot": "docs/harness-engineering",
    "config": "docs/harness-engineering/harness-engineering.json",
    "convention": "Plugin-owned, docs-adjacent, target-specific, machine-readable, and namespaced metadata separate from root CLAUDE.md, AGENTS.md, and ARCHITECTURE.md.",
    "entrypoint": "CLAUDE.md",
    "compatibilitySymlink": "AGENTS.md",
    "compatibilitySymlinkTarget": "CLAUDE.md",
    "compatibilitySymlinkStatus": "required",
    "architecture": "ARCHITECTURE.md",
    "guardrails": "docs/harness-engineering/guardrails.md",
    "knownViolations": "docs/harness-engineering/known-violations.md",
    "readiness": "docs/harness-engineering/readiness.md",
    "updates": "docs/harness-engineering/updates.md"
  },
  "scripts": {
    "root": "scripts/harness",
    "validator": "scripts/harness/validate_harness.py",
    "wrapper": "scripts/harness/validate_harness.sh"
  },
  "checks": {
    "requiredCommands": [
      "sh scripts/harness/validate_harness.sh"
    ],
    "optionalCommands": [],
    "allowedCommandPrefixes": [
      "sh scripts/harness/validate_harness.sh"
    ],
    "allowedCommandPrefixesNote": "Informational only. The validator enforces exact argv matches for required and optional commands. Select the command that matches the CI language/toolchain available in the target repository."
  },
  "ci": {
    "provider": "github-actions",
    "githubWorkflow": ".github/workflows/harness-checks.yml",
    "gitlabConfig": ".gitlab-ci.yml",
    "branches": {
      "push": [
        "develop",
        "main",
        "master"
      ],
      "pullRequest": [
        "develop",
        "main",
        "master"
      ],
      "mergeRequest": [
        "develop",
        "main",
        "master"
      ]
    }
  },
  "hooks": {
    "enabled": false,
    "path": ".githooks",
    "commitMsg": ".githooks/commit-msg",
    "prePush": ".githooks/pre-push"
  }
}
```

## Procedure

### 1. Inspect the target repository

1. Read existing `CLAUDE.md`, `AGENTS.md`, `ARCHITECTURE.md`, `docs/`, CI files, hooks, and harness scripts if they exist.
2. Check whether `docs/harness-engineering/harness-engineering.json` already exists.
3. Treat root `.harness-engineering.json` as manual migration input only.
4. Identify configured docs, scripts, CI, hooks, validation commands, and pending install conflicts.
5. Record conflicts before writing files.

### 2. Create or update config

1. Start from the inline JSON template or `assets/config.json.template`.
2. Keep exactly the six canonical top-level fields: `stage`, `docs`, `scripts`, `checks`, `ci`, and `hooks`.
3. Keep `checks.requiredCommands` and `checks.allowedCommandPrefixes` limited to `sh scripts/harness/validate_harness.sh` unless a separate integration pack explicitly owns additional trusted commands.
4. Keep CI provider and hook paths declared before installing templates.
5. Put optional project-specific metadata such as source roots, layer models, generated-doc policy, or custom requirement rules outside the canonical template unless the target repository explicitly owns that extension.

### 3. Install stage-appropriate files

Perform a dry-run inventory first.

1. Map each asset to its configured target path.
2. For each target path, classify it as `create`, `update`, `conflict`, or `skip`.
3. Create missing directories only under configured docs, scripts, CI, and hooks roots.
4. Scaffold root `CLAUDE.md` from `assets/CLAUDE.md.template` as a short map that points to `docs/harness-engineering/harness-engineering.json`, root `ARCHITECTURE.md`, and configured docs paths.
5. Create `AGENTS.md -> CLAUDE.md` when no separate `AGENTS.md` already exists. Do not create `CLAUDE.md -> AGENTS.md` because that reverses the dependency direction. If an existing detailed `AGENTS.md` cannot be replaced, set `docs.compatibilitySymlinkStatus` to `pending-conflict` and record the migration decision in `docs/harness-engineering/updates.md`.
6. Create `.agents/skills` as a symlink to `../.claude/skills` when both skill inventories are used by the target repo.
7. Copy or adapt docs scaffolds from `assets/docs-directory-scaffold.md.template` and `assets/harness-docs-update.md.template`.
8. Install `scripts/harness/validate_harness.py` and `scripts/harness/validate_harness.sh`.
9. Run the wrapper with `sh scripts/harness/validate_harness.sh`; the wrapper prefers local `python3`, falls back to local `python`, and uses `uvx --offline --no-python-downloads python` only when no local Python command is available.
10. Install CI from `assets/github-actions.yml.template` or `assets/gitlab-ci.yml.template` only when the stage promotes checks to CI.
11. Install hook templates from `assets/git-hooks.md.template` into the configured hooks path only after documenting `git config core.hooksPath .githooks`.

### 4. Validate gates

Run the configured validator and every safe command listed under `checks.requiredCommands`.

```bash
sh scripts/harness/validate_harness.sh
```

Validation must confirm:

- `docs/harness-engineering/harness-engineering.json` is valid JSON and declares exactly six top-level fields: `stage`, `docs`, `scripts`, `checks`, `ci`, and `hooks`.
- `ARCHITECTURE.md` is root-level by default; `docs/ARCHITECTURE.md` is not the OpenAI-profile default.
- `CLAUDE.md` is the default repository-level instruction map and is not an encyclopedia; `AGENTS.md` points to it.
- Harness docs exist: `guardrails.md`, `known-violations.md`, `readiness.md`, and `updates.md`.
- Declared docs and scripts paths exist or are reported as pending install actions.
- `AGENTS.md` is a symlink to `CLAUDE.md` when `docs.compatibilitySymlinkStatus` is `required`; pending conflicts are explicitly recorded instead of overwritten.
- Configured validation commands must match the harness validator argv form and must not contain shell metacharacters; `checks.allowedCommandPrefixes` is informational only.
- `.agents/skills` is a symlink to `../.claude/skills` when present.
- CI points to the configured provider and calls the configured harness validator when Stage 3 is enabled.
- Git hooks call configured advisory checks without requiring network access when Stage 2 is enabled.
- Full specs, ADR constraints, architecture docs, scripts, and implementation paths are consistent enough for an agent to follow repository documentation before changing code.

### 5. Update and ratchet

Run the update path whenever docs, scripts, CI provider, hooks, guardrails, or validation commands change.

1. Read `docs/harness-engineering/harness-engineering.json` and compare it with the current repository.
2. Update config first, then harness docs, then scripts, then CI and hooks.
3. Record meaningful changes in `docs/harness-engineering/updates.md` or the configured execution plan.
4. Re-run validation and fix drift introduced by the update.
5. Move checks from `warn` to `error` only when readiness and known-violation budgets satisfy the stage exit gate.
6. Report remaining conflicts instead of forcing overwrites.

## Optional Integration Packs

Use these packs only when the target repository explicitly wants a project-tooling entrypoint for the same harness-engineering contract. Do not add their commands to `checks.requiredCommands` or `checks.allowedCommandPrefixes` by default; the target should enable only command forms supported by its CI toolchain.

| Pack | Asset path | Target shape | Command surface |
| --- | --- | --- | --- |
| Node validator | `assets/node-validator/validate-harness.mjs.template` | `scripts/harness/validate-harness.mjs` or package-script entrypoint | Node >=18 runs Node-native harness config validation |
| Gradle plugin | `assets/gradle-plugin/` | included build-logic convention plugin by default; `buildSrc` is an alternative | `./gradlew harnessCheck` runs Gradle-native harness config validation |
| Maven plugin | `assets/maven-plugin/` | local Maven plugin module | `mvn local.harness:harness-maven-plugin:harness-check` runs Maven-native harness config validation |

Keep pack documentation beside the adopted target files. If a pack becomes shared enforcement, record the exact command and owner in the target harness docs before enabling error gates.

## Edge cases

- If a target repo still has root `.harness-engineering.json`, treat it as unsupported manual migration input, move the durable values into `docs/harness-engineering/harness-engineering.json`, and leave a short migration note rather than preserving two config conventions.
- If the target repo already has a detailed `AGENTS.md`, do not replace it. Set `docs.compatibilitySymlinkStatus` to `pending-conflict`, report the conflict, and propose migrating durable guidance into root `CLAUDE.md` plus configured deeper docs before creating `AGENTS.md -> CLAUDE.md`.
- If the target repo has no clear architecture model, document the provisional assumption in root `ARCHITECTURE.md` and `docs/harness-engineering/guardrails.md` rather than adding it to the canonical config template.
- If source schemas live under language-specific paths, keep that as optional project-specific metadata outside the canonical config template.
- If a check cannot be run offline, remove it from ordinary guardrails and document it as optional project-specific follow-up.
- If a repository needs build-tool-specific guardrails, use the Node, Gradle, or Maven integration pack only after documenting the adopted files, trusted command form, and validation ownership.

## Output contract

Return:

1. The target repository config path and final `docs/harness-engineering/harness-engineering.json` summary
2. The current staged guardrail phase and exit gate status
3. Created, updated, skipped, and conflicted files
4. Installed or updated docs, scripts, CI, hooks, and validation command status
5. Validation commands run and their results
6. Remaining risks, assumptions, known violations, or no-overwrite decisions

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

- `assets/config.json.template` - copy or diff when creating or updating `docs/harness-engineering/harness-engineering.json`
- `assets/CLAUDE.md.template` - copy or diff when creating or updating the root `CLAUDE.md` map
- `assets/docs-directory-scaffold.md.template` - copy or diff when scaffolding or updating configured docs and harness docs paths
- `assets/execution-plan.md.template` - copy or adapt when the target repo needs a configured execution-plan template
- `assets/harness-docs-update.md.template` - copy when recording harness updates, readiness changes, and docs/script alignment
- `assets/github-actions.yml.template` - copy or diff when `ci.provider` is `github-actions` and Stage 3 error gates are enabled
- `assets/gitlab-ci.yml.template` - copy or diff when `ci.provider` is `gitlab-ci` and Stage 3 error gates are enabled
- `assets/git-hooks.md.template` - open when installing advisory commit and push hook templates
- `assets/gitignore.md.template` - copy entries into the target root `.gitignore` when installing harness scripts or local generated caches
- `scripts/validate_harness.py` and `scripts/validate_harness.sh` - copy into target `scripts/harness/` when an offline validator and direct wrapper are needed
- `assets/node-validator/validate-harness.mjs.template` - optional Node >=18 no-dependency validator for repositories that expose harness validation through Node tooling
- `assets/gradle-plugin/` - optional Gradle convention plugin pack that registers `harnessCheck` for Gradle-native config validation
- `assets/maven-plugin/` - optional Maven plugin pack that registers a `harness-check` goal for Maven-native config validation
- `references/bootstrap-apply-flow.md` - open when the target repository is empty or the apply/update sequence needs a deeper conflict plan
- `references/architecture-enforcement.md` - open when translating the configured layer model, scoped path rules, ADRs, or specs into deterministic checks
- `references/ci-hooks-integration.md` - open when wiring GitHub Actions, GitLab CI, git hooks, or optional Node, Gradle, and Maven integration packs together
- `references/docs-as-context.md` - open when docs, full specs, ADRs, scripts, and implementation paths drift apart
- `references/entropy-management.md` - open when configuring readiness scoring, ratchets, doc freshness, or known-violation cleanup
- `references/source-verification.md` - open when updating or reviewing the OpenAI article source mapping
