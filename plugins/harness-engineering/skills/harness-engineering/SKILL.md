---
name: harness-engineering
description: >-
  Apply staged setup-time guardrails for target repositories. Use this skill when installing, updating, ratcheting, or validating repository configuration scaffolding for docs, scripts, CI, hooks, Gradle checks, Node package scripts, customizable warn/error requirement rules, architecture rules, readiness scoring, and agent context from docs/harness-engineering/harness-engineering.json.
compatibility: >-
  Ordinary authoring is offline. Target repositories that copy and run the bundled validator wrapper need `uvx` available locally or in CI; the direct Python validator remains inspectable source.
---

# Harness Engineering

Install, update, ratchet, and validate staged repository guardrails that make a target repository visible, agent-legible, and mechanically checkable through its normal docs, script, CI, hook, and build surfaces.

## Operating rules

- Treat `docs/harness-engineering/harness-engineering.json` as the ordinary target-repo config path and source of truth for both initial creation and later updates.
- Mention root `.harness-engineering.json` only as a legacy migration fallback. Do not use it for new setup.
- Use staged adoption: document first, advise locally, then enforce in shared gates only after readiness is visible.
- Use `warn` and `error` as the only enforcement levels. Every configurable rule family must declare its level in `enforcement.settings`, and each user requirement rule must declare its own `severity`.
- Convert fixed architecture assumptions into defaults or examples. The target config's `layerModel`, `sourceRoots`, `checks`, and scoped path rules decide what applies.
- Keep ordinary use offline. Do not require live docs, registries, hosted validators, or network access to install or validate guardrails.
- Install non-destructively: dry-run first, report conflicts, and do not overwrite existing target files unless the caller explicitly asks for replacement.
- Keep docs and scripts evolving together. A documented rule needs a matching check, command, or explicit reason it is informational only.
- Default to the verified OpenAI article structure while preserving this repository's dependency direction: root `CLAUDE.md` is the repository-level rule map, `AGENTS.md -> CLAUDE.md` exposes the same map to AGENTS.md readers, root `ARCHITECTURE.md` stays outside `docs/`, and `docs/` holds deeper design docs, execution plans, generated docs, product specs, references, and top-level topic docs. Open `references/source-verification.md` when updating the source mapping.
- Keep `.agents/skills -> ../.claude/skills` when both skill surfaces exist.

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
├── .githooks/
│   ├── commit-msg
│   └── pre-push
├── build.gradle.kts
└── package.json
```

Install only the CI and build-system files the target repository declares. GitHub Actions, GitLab CI, git hooks, Gradle, Node package scripts, and standalone script lanes are integration options, not universal requirements.

## Staged setup model

Use stages to make guardrails firm without surprising the repository.

| Stage | Purpose | Installed surfaces | Exit gate |
| --- | --- | --- | --- |
| Stage 0 discovery | Inventory current docs, scripts, source roots, CI, hooks, build files, and known violations | Dry-run report only | Existing surfaces and conflicts are listed |
| Stage 1 visibility/docs/config | Make repository configuration visible under `docs/harness-engineering/` | Config, `CLAUDE.md`, compatibility symlinks, guardrails/readiness/updates docs | Config validates in `--template` or advisory mode |
| Stage 2 advisory scripts/hooks | Add local guardrails without failing shared integration | Validator script, optional `.githooks/*`, known-violation ledger | Validator runs locally and reports `warn` findings for legacy debt |
| Stage 3 error shared gates | Promote agreed checks to shared enforcement | GitHub Actions or GitLab CI, optional Gradle `check`, Node package script, or standalone script wiring | Configured required commands pass through the selected integration and `error` findings fail |
| Stage 4 ratchet/freshness/maturity hardening | Tighten quality gates over time | Readiness score, maturity level, doc freshness gate, ratchet thresholds | New `error` findings fail and known violations trend down |

## Procedure

### 1. Inspect the target repository

Read the repository before proposing files.

1. Identify existing agent entrypoints: `AGENTS.md`, `CLAUDE.md`, `.claude/`, `.agents/`, and project agent directories.
2. Identify docs roots, specs, ADRs, source roots, schema sources, generated docs, tests, scripts, CI provider, hooks, Gradle files, Node package manager files, standalone check wrappers, package managers, and validation commands.
3. Detect whether GitHub Actions (`.github/workflows/`) or GitLab CI (`.gitlab-ci.yml`) already exists. If both exist, keep both advisory until the target maintainer chooses the shared error-gate provider.
4. Read current root `ARCHITECTURE.md`, specs, and ADR constraints before deriving a layer model or scoped path rules.
5. Record conflicts, existing files, maturity level, known violations, and no-overwrite decisions before installation.

### 2. Configure visible guardrails

Create or update `docs/harness-engineering/harness-engineering.json` with the target repository's real paths, stages, rules, and per-setting enforcement levels. This path is used for initial creation and every later modification.

```json
{
  "schemaVersion": 1,
  "projectProfile": {
    "name": "generic",
    "description": "OpenAI-style default repo harness; switch to spring-boot or another profile when the project type needs different paths."
  },
  "stage": {
    "current": 1,
    "target": 3,
    "mode": "advisory",
    "exitGate": "Config, docs, validator, and known-violation ledger exist before CI treats error-level findings as failures."
  },
  "docs": {
    "harnessRoot": "docs/harness-engineering",
    "config": "docs/harness-engineering/harness-engineering.json",
    "guardrails": "docs/harness-engineering/guardrails.md",
    "knownViolations": "docs/harness-engineering/known-violations.md",
    "readiness": "docs/harness-engineering/readiness.md",
    "updates": "docs/harness-engineering/updates.md",
    "entrypoint": "CLAUDE.md",
    "compatibilitySymlink": "AGENTS.md",
    "compatibilitySymlinkTarget": "CLAUDE.md",
    "compatibilitySymlinkStatus": "required",
    "root": "docs",
    "architecture": "ARCHITECTURE.md",
    "design": "docs/DESIGN.md",
    "frontend": "docs/FRONTEND.md",
    "plansDoc": "docs/PLANS.md",
    "productSense": "docs/PRODUCT_SENSE.md",
    "reliability": "docs/RELIABILITY.md",
    "security": "docs/SECURITY.md",
    "quality": "docs/QUALITY_SCORE.md",
    "designDocs": "docs/design-docs",
    "productSpecs": "docs/product-specs",
    "plans": {
      "active": "docs/exec-plans/active",
      "completed": "docs/exec-plans/completed",
      "debt": "docs/exec-plans/tech-debt-tracker.md"
    },
    "generated": "docs/generated",
    "references": "docs/references"
  },
  "scripts": {
    "root": "scripts/harness",
    "validator": "scripts/harness/validate_harness.py",
    "wrapper": "scripts/harness/validate_harness.sh"
  },
  "sourceRoots": ["src"],
  "testRoots": ["test", "tests"],
  "schemaSources": [
    {
      "kind": "generic",
      "paths": ["schema.graphqls", "prisma/schema.prisma", "src/schema"]
    }
  ],
    "generatedArtifacts": [
      {
        "path": "docs/generated/db-schema.md",
        "sources": ["src/schema"],
        "regenerate": "sh scripts/harness/validate_harness.sh",
        "policy": "Derived documentation only; source schemas remain in project-specific source roots."
      }
  ],
  "generatedPolicy": {
    "root": "docs/generated",
    "requiresProvenance": true,
    "requiresRegenerationCommand": true,
    "sourceSchemasStayOutsideGeneratedDocs": true
  },
  "projectProfiles": {
    "generic": {
      "sourceRoots": ["src"],
      "schemaSources": ["schema.graphqls", "prisma/schema.prisma", "src/schema"],
      "buildIntegration": ["standalone", "node"]
    },
    "spring-boot": {
      "sourceRoots": ["src/main/java", "src/main/kotlin"],
      "testRoots": ["src/test/java", "src/test/kotlin"],
      "buildIntegration": ["gradle", "node"],
      "schemaSources": [
        "src/main/resources/db/migration",
        "src/main/resources/db/changelog",
        "src/main/resources/openapi",
        "src/main/proto",
        "src/main/resources/graphql/schema.graphqls",
        "src/main/java/**/entity",
        "src/main/kotlin/**/entity"
      ],
      "generatedArtifacts": ["docs/generated/db-schema.md", "docs/generated/api-schema.md", "docs/generated/runtime-config.md"]
    }
  },
  "scopedPathRules": [
    {
      "path": "src/domains/**",
      "rule": "Follow the configured layer model and ADR constraints."
    }
  ],
  "layerModel": {
    "name": "default-domain-layers",
    "roots": ["src/domains"],
    "layers": ["types", "config", "repo", "providers", "service", "runtime", "ui"],
    "allowedEdges": {
      "types": [],
      "config": ["types"],
      "repo": ["config", "types"],
      "providers": ["repo", "config", "types"],
      "service": ["providers", "repo", "config", "types"],
      "runtime": ["service", "providers", "repo", "config", "types"],
      "ui": ["runtime", "service", "providers", "types"]
    },
    "crossCutting": {
      "path": "src/providers",
      "interfaceFile": "interface"
    }
  },
  "checks": {
    "requiredCommands": [
      "sh scripts/harness/validate_harness.sh"
    ],
    "optionalCommands": [],
    "allowedCommandPrefixes": [
      "sh scripts/harness/validate_harness.sh",
      "uvx --offline --no-python-downloads python scripts/harness/validate_harness.py",
      "./gradlew harnessCheck",
      "npm run harness:validate",
      "pnpm run harness:validate",
      "yarn run harness:validate",
      "bun run harness:validate"
    ],
    "allowedCommandPrefixesNote": "Informational only. The validator enforces exact argv matches for required and optional commands: `sh scripts/harness/validate_harness.sh`, `uvx --offline --no-python-downloads python scripts/harness/validate_harness.py --config docs/harness-engineering/harness-engineering.json`, `./gradlew harnessCheck`, and the Node package-manager `run harness:validate` forms.",
    "docs": true,
    "architecture": true,
    "links": true,
    "noOverwrite": true
  },
  "enforcement": {
    "defaultSeverity": "warn",
    "allowedSeverities": ["warn", "error"],
    "settings": {
      "docs": "warn",
      "architecture": "warn",
      "links": "warn",
      "noOverwrite": "error",
      "generatedPolicy": "error",
      "userRequirementRules": "warn",
      "ci": "warn",
      "hooks": "warn",
      "gradle": "warn",
      "node": "warn",
      "standalone": "warn"
    }
  },
  "readiness": {
    "maturityLevel": 1,
    "minimumBlockingLevel": 3,
    "scorePath": "docs/harness-engineering/readiness.md",
    "knownViolations": "docs/harness-engineering/known-violations.md",
    "ratchet": {
      "mode": "warn",
      "newViolations": "error",
      "knownViolationBudget": 100,
      "reduceBudgetBy": 5
    },
    "freshness": {
      "docsMaxAgeDays": 30,
      "adrRequiredForScopedRules": true,
      "specRequiredForChangedBehavior": true
    }
  },
  "ci": {
    "provider": "github-actions",
    "githubWorkflow": ".github/workflows/harness-checks.yml",
    "gitlabConfig": ".gitlab-ci.yml",
    "branches": {
      "push": ["develop", "main", "master"],
      "pullRequest": ["develop", "main", "master"],
      "mergeRequest": ["develop", "main", "master"]
    },
    "customizationNote": "Default CI runs on merge requests or pull requests and pushes to develop, main, and master. Adjust branch lists, path filters, or provider rules to match user preference before enabling error gates."
  },
  "hooks": {
    "enabled": true,
    "path": ".githooks",
    "commitMsg": ".githooks/commit-msg",
    "prePush": ".githooks/pre-push"
  },
  "gradle": {
    "enabled": false,
    "buildFile": "build.gradle.kts",
    "taskName": "harnessCheck"
  },
  "node": {
    "enabled": false,
    "packageFile": "package.json",
    "packageManager": "auto",
    "scriptName": "harness:validate",
    "installCommand": "auto",
    "runCommand": "auto run harness:validate",
    "workspaceMode": "root-or-workspace"
  },
  "standalone": {
    "enabled": false,
    "entrypoint": "scripts/harness/validate_harness.sh",
    "commands": ["sh scripts/harness/validate_harness.sh"],
    "description": "Standalone shell/script lane for repositories without a CI or build-system integration."
  },
  "userRequirementRules": [
    {
      "name": "document-user-requirements",
      "type": "documentation-link",
      "severity": "warn",
      "scope": "docs/product-specs/**/*.md",
      "description": "User-facing requirements must name their source, acceptance criteria, and validation surface.",
      "validation": "review docs/product-specs and configured checks before promoting to error"
    },
    {
      "name": "protect-generated-sources",
      "type": "generated-boundary",
      "severity": "error",
      "scope": "docs/generated/**",
      "description": "Generated docs are derived artifacts only and must not become source schemas.",
      "validation": "sh scripts/harness/validate_harness.sh"
    }
  ],
  "yamlStyle": {
    "literalMultiline": "|-",
    "foldedMultiline": ">-"
  }
}
```

Copy `assets/config-template.json` when starting from scratch, or diff it against the existing config during updates. Update config before copying docs, scripts, CI, or hooks so every installed artifact has a declared path and severity level.

### 3. Install stage-appropriate files

Perform a dry-run inventory first.

1. Map each asset to its configured target path.
2. For each target path, classify it as `create`, `update`, `conflict`, or `skip`.
3. Create missing directories only under configured docs, scripts, CI, and hooks roots.
4. Scaffold root `CLAUDE.md` from `assets/claude-md-template.md` as a short map that points to `docs/harness-engineering/harness-engineering.json`, root `ARCHITECTURE.md`, and configured docs paths.
5. Create `AGENTS.md -> CLAUDE.md` when no separate `AGENTS.md` already exists. Do not create `CLAUDE.md -> AGENTS.md` because that reverses the dependency direction. If an existing detailed `AGENTS.md` cannot be replaced, set `docs.compatibilitySymlinkStatus` to `pending-conflict` and record the migration decision in `docs/harness-engineering/updates.md`.
6. Create `.agents/skills` as a symlink to `../.claude/skills` when both skill inventories are used by the target repo.
7. Copy or adapt docs scaffolds from `assets/docs-directory-scaffold.md` and `assets/harness-docs-update.md`.
8. Install `scripts/harness/validate_harness.py` and `scripts/harness/validate_harness.sh`. Run the direct wrapper with `sh scripts/harness/validate_harness.sh`; the wrapper must call `uvx --offline --no-python-downloads python scripts/harness/validate_harness.py` so target repositories do not depend on a system Python interpreter command, do not download Python, and do not resolve packages from the network during validation.
9. Install CI from `assets/github-actions.yml` or `assets/gitlab-ci.yml` only when the stage promotes checks to CI.
10. Install hook templates from `assets/git-hooks.md` into the configured hooks path only after documenting `git config core.hooksPath .githooks`.
11. Add the Gradle snippet from `assets/gradle-integration.gradle.kts` only when `gradle.enabled` is true or the target repo explicitly wants Gradle `check` integration.
12. Add the package-script snippet from `assets/node-integration-package-json.md` only when `node.enabled` is true or the target repo explicitly wants npm, pnpm, Yarn, Bun, Turbo, Nx, or workspace validation through `harness:validate`.
13. Apply `userRequirementRules` only after mapping each rule to a documented source, scope, `warn` or `error` severity, and validation surface.

### 4. Validate gates

Run the configured validator and every safe command listed under `checks.requiredCommands`.

```bash
sh scripts/harness/validate_harness.sh
```

Validation must confirm:

- `docs/harness-engineering/harness-engineering.json` is valid JSON and declares docs paths, scripts paths, source roots, layer model, checks, per-setting `warn`/`error` enforcement, CI provider, hooks, build integrations, user requirement rules, YAML style, readiness, and staged gate metadata.
- `ARCHITECTURE.md` is root-level by default; `docs/ARCHITECTURE.md` is not the OpenAI-profile default.
- `CLAUDE.md` is the default repository-level instruction map and is not an encyclopedia; `AGENTS.md` points to it.
- Harness docs exist: `guardrails.md`, `known-violations.md`, `readiness.md`, and `updates.md`.
- Source schema locations stay in checked-in project sources, while generated docs under `docs/generated/` declare provenance, source paths, and regeneration commands.
- Declared docs and scripts paths exist or are reported as pending install actions.
- `AGENTS.md` is a symlink to `CLAUDE.md` when `docs.compatibilitySymlinkStatus` is `required`; pending conflicts are explicitly recorded instead of overwritten.
- Configured validation commands must match one of the validator-trusted argv forms and must not be auto-run when introduced or modified in the current change set without review; `checks.allowedCommandPrefixes` is informational only.
- `.agents/skills` is a symlink to `../.claude/skills` when present.
- CI points to the configured provider and calls the configured harness validator when Stage 3 is enabled.
- Git hooks call configured advisory checks without requiring network access when Stage 2 is enabled.
- Gradle `check`, Node package scripts, or standalone script lanes call the configured harness task when their integration is enabled.
- Full specs, ADR constraints, architecture docs, scripts, and implementation paths are consistent enough for an agent to follow repository documentation before changing code.

### 5. Update and ratchet

Run the update path whenever docs, scripts, source roots, CI provider, hooks, build-system files, user requirement rules, guardrails, readiness, or the layer model changes.

1. Read `docs/harness-engineering/harness-engineering.json` and compare it with the current repository.
2. Update config first, then harness docs, then scripts, then CI/hooks/build integrations.
3. Record meaningful changes in `docs/harness-engineering/updates.md` or the configured execution plan.
4. Re-run validation and fix drift introduced by the update.
5. Move checks from `warn` to `error` only when readiness and known-violation budgets satisfy the stage exit gate.
6. Report remaining conflicts instead of forcing overwrites.

## Edge cases

- If a target repo still has root `.harness-engineering.json`, migrate it to `docs/harness-engineering/harness-engineering.json` and leave a short compatibility note rather than preserving two authoritative configs.
- If the target repo already has a detailed `AGENTS.md`, do not replace it. Set `docs.compatibilitySymlinkStatus` to `pending-conflict`, report the conflict, and propose migrating durable guidance into root `CLAUDE.md` plus configured deeper docs before creating `AGENTS.md -> CLAUDE.md`.
- If the target repo has no clear architecture model, set `layerModel.name` to a provisional value and document the assumption in root `ARCHITECTURE.md` and `docs/harness-engineering/guardrails.md`.
- If the target repo is Spring Boot, keep Spring-specific source roots, Flyway/Liquibase migrations, JPA/entity packages, OpenAPI/protobuf/GraphQL paths, Gradle wiring, optional Node frontend checks, and generated docs as profile config, not universal defaults.
- If source schemas live under language-specific paths, keep them there and generate `docs/generated/*` artifacts from them with provenance.
- If the repo is not a GitHub Actions, GitLab CI, Gradle, or Node project, keep those integrations disabled and install only docs, scripts, hooks, and standalone lanes requested by config.
- If a check cannot be run offline, remove it from ordinary guardrails and document it as optional project-specific follow-up.

## Output contract

Return:

1. The target repository config path and final `docs/harness-engineering/harness-engineering.json` summary
2. The current staged guardrail phase and exit gate status
3. Created, updated, skipped, and conflicted files
4. Installed or updated docs, scripts, CI, hooks, build integrations, and user requirement rule status
5. Validation commands run and their results
6. Remaining risks, assumptions, known violations, or no-overwrite decisions

## Gotchas

- Do not impose the default `types -> config -> repo -> service -> runtime -> ui` model when the target repo declares a different model.
- Do not copy CI for both providers unless the target repo intentionally runs both.
- Do not make the root instruction entrypoint an encyclopedia. Repository-level rules start in `CLAUDE.md`, deeper rules live in configured docs paths, and `AGENTS.md` points to it.
- Do not store source schemas in `docs/generated/`; that directory is for reproducible derived documentation.
- Do not let docs describe checks that no script, CI job, hook, Gradle task, Node package script, standalone lane, or explicit manual process can verify.
- Do not hard-code user requirement rules into generic defaults. Put project-specific rules in `userRequirementRules` with `warn` or `error` severity and a validation surface.
- Do not use bare YAML `|` or `>` in templates; use `|-` for literal multiline blocks and `>-` for folded multiline text.
- Do not auto-run commands newly introduced or modified by the current change until a reviewer confirms they match one of the validator-trusted command forms and do not contain shell metacharacters.
- Do not advance from advisory to `error` gates until readiness, maturity level, and known-violation ledger rules are satisfied.
- Do not require network access for ordinary validation.

## Support files

- `assets/config-template.json` - copy or diff when creating or updating `docs/harness-engineering/harness-engineering.json`
- `assets/claude-md-template.md` - copy or diff when creating or updating the root `CLAUDE.md` map
- `assets/docs-directory-scaffold.md` - copy or diff when scaffolding or updating configured docs and harness docs paths
- `assets/harness-docs-update.md` - copy when recording harness updates, readiness changes, and docs/script alignment
- `assets/github-actions.yml` - copy or diff when `ci.provider` is `github-actions` and Stage 3 error gates are enabled
- `assets/gitlab-ci.yml` - copy or diff when `ci.provider` is `gitlab-ci` and Stage 3 error gates are enabled
- `assets/git-hooks.md` - open when installing advisory commit and push hook templates
- `assets/gitignore-template.md` - copy entries into the target root `.gitignore` when installing harness scripts or local generated caches
- `assets/gradle-integration.gradle.kts` - copy when Gradle `check` integration is enabled
- `assets/node-integration-package-json.md` - copy when Node package-script integration is enabled
- `scripts/validate_harness.py` and `scripts/validate_harness.sh` - copy into target `scripts/harness/` when an offline validator and direct wrapper are needed
- `references/bootstrap-apply-flow.md` - open when the target repository is empty or the apply/update sequence needs a deeper conflict plan
- `references/architecture-enforcement.md` - open when translating the configured layer model, scoped path rules, ADRs, or specs into deterministic checks
- `references/ci-hooks-gradle-integration.md` - open when wiring GitHub Actions, GitLab CI, git hooks, Gradle, Node package scripts, or standalone lanes together
- `references/docs-as-context.md` - open when docs, full specs, ADRs, scripts, and implementation paths drift apart
- `references/entropy-management.md` - open when configuring readiness scoring, ratchets, doc freshness, or known-violation cleanup
- `references/agent-legibility.md` - open when target repo agents need runtime evidence, observability, worktree isolation, or `.claude/agents/` guidance
- `references/source-verification.md` - open when updating or reviewing the OpenAI article source mapping
