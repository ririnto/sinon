---
name: harness-evolve
description: >-
  Reconcile an installed repository harness after real project use changes docs, templates, agents, skills, validators, generated-artifact policy, CI, hooks, or readiness gates. Use this skill when repeated validation or review failures or committed harness drift show the target harness contract should evolve and produce a versioned evolution plan with validation impact.
argument-hint: '[summary-of-delta]'
allowed-tools:
  - Read
  - Grep
  - Glob
  - LS
  - Bash(git diff *)
  - Bash(git status *)
---

# Harness Evolve

Review how the installed target harness changed and produce a versioned evolution plan. This plugin skill does not weaken validation to make failures disappear; it updates the harness contract when project reality has legitimately changed.

This skill is report-only unless the user separately asks for implementation. Produce the evolution plan first, then use `harness-validate` after changes are implemented.

## First Safe Checks

1. Read `AGENTS.md`, `docs/harness/README.md`, `docs/harness/manifest.json`, and any active `docs/exec-plans/active/yyyy-MM-dd-*.md` that touches the harness.
2. Inspect the user-provided delta summary and current `git diff` for harness-owned files.
3. Separate product changes from harness changes.
4. Confirm that every proposed harness change has a matching validation path or an explicit informational-only reason.
5. Check CI examples when validation commands, stack mode, or generated-artifact policy changes.

```sh
git status --short
```

```sh
git diff -- AGENTS.md CLAUDE.md ARCHITECTURE.md docs .claude
```

Use the stack validation command from `docs/harness/README.md`; do not guess a command when the installed README already names one.

## Workflow

1. Classify the change.

    | Change type | Typical files |
    | --- | --- |
    | Context contract | `AGENTS.md`, `CLAUDE.md`, `ARCHITECTURE.md` |
    | Documentation structure | `docs/**`, `docs/harness/templates/docs/**` |
    | Work surface | `.claude/agents/**`, `.claude/skills/**` |
    | Validation surface | `docs/harness/**`, CI snippets, hook templates |
    | Generated artifacts | `docs/generated/**`, generated-artifact template, manifest policy |

2. Decide whether the change is a legitimate evolution or a local drift.
3. Keep valid minor improvements; reject only changes that break the harness contract or product fit.
4. Update the evolution plan so docs, templates, agents, skills, validators, and `.gitkeep` placeholders stay aligned.
5. Identify the stack-specific validation command that must pass after implementation.

## Evolution Decision Table

| Situation | Decision | Required action |
| --- | --- | --- |
| Product architecture changed and docs are stale | evolve | Update `ARCHITECTURE.md`, relevant design docs, and validation expectations together. |
| A generated artifact moved or was renamed | evolve | Update `docs/harness/manifest.json`, generated-artifact docs, and any template references. |
| CI should run the same final check command on a new platform | evolve | Add or update CI template examples while preserving the selected pre-push command. |
| Validation fails because a required file was deleted accidentally | reject as drift | Restore the file or re-run installation; do not weaken the contract. |
| Placeholder content is still generic after installation | defer | Ask for target truth or create the relevant spec/design task first. |
| A seed reference no longer fits the target stack | evolve | Replace the seed reference and record why the new source is target-specific. |
| Product code changed but harness contracts still match reality | reject as drift | Report that the change is product work, not harness evolution. |
| Validator is too strict for ignored or generated files | evolve | Adjust validator rules only after confirming gitignore-aware behavior. |
| Proposed change removes validation to make a failure pass | reject as drift | Keep validation and fix the underlying contract or target content. |

## Lifecycle Stages

Use the project phase to decide how strict the evolution should be.

| Phase | Harness posture |
| --- | --- |
| Discovery | Prefer placeholders, product specs, design docs, and active plans; WARN items are acceptable when documented. |
| Implementation | Require active execution plans, stack validation, and CI examples that run the selected command. |
| Hardening | Ratchet stable WARN findings into ERROR checks and remove obsolete placeholders. |
| Release | Require CI/hook documentation and generated artifacts to be fresh or explicitly waived. |
| Maintenance | Remove obsolete seeds/templates and record contract updates in the evolution log. |

## Change Set Checklist

Use this checklist before proposing or applying harness evolution.

- `AGENTS.md` and `CLAUDE.md` still describe the same target contract.
- `docs/harness/README.md` names the current validation command.
- `docs/harness/manifest.json` matches required files, optional seed files, empty directory keep files, and generated-artifact policy.
- `.claude/agents/**` and `.claude/skills/**` remain self-sufficient for target repository use.
- `docs/harness/templates/**` still contain placeholders only where the template renderer or human copy step expects them.
- `.github/workflows/harness.yml` and `.gitlab-ci.yml`, when present, run the selected final check command.
- `docs/generated/**` contains real generated artifacts or `.gitkeep`, not fake readiness files.
- A `docs/exec-plans/active/yyyy-MM-dd-<slug>.md` entry records the reason, files changed, validation command, and remaining follow-up for any non-trivial evolution.

## Evolution Plan Entry

Record the evolution in a new or existing execution plan under `docs/exec-plans/active/` using the project's standard `yyyy-MM-dd-<slug>.md` template. The plan SHOULD include:

```markdown
## Trigger

<validation failure, review finding, product change, or stack migration>

## Decision

<evolve|reject as drift|defer>

## Files

<changed harness files or planned files>

## Validation

<exact command that must pass>

## Follow-up

<remaining target-specific content or none>
```

## Examples

### CI platform added

```text
delta: Target repository needs GitLab CI in addition to GitHub Actions.
decision: evolve
contract updates: add `.gitlab-ci.yml` harness job that runs the same generated pre-push final check command.
validation impact: local harness validation stays unchanged; CI mirrors the generated pre-push final check command.
risks: image tags may need pinning under strict supply-chain policy.
```

### Generated artifact removed

```text
delta: `docs/generated/api-schema.md` was deleted because the project no longer exposes that API.
decision: evolve only if the manifest and generated-artifact docs also remove the requirement.
contract updates: update `docs/harness/manifest.json` and generated-artifact index.
validation impact: selected stack command must pass without requiring the removed artifact.
risks: downstream docs may still link to the old artifact.
```

### Harness evolution plan

```markdown
## Harness Evolution Plan

- Delta: GitLab CI examples added for all stack templates.
- Decision: Evolve harness contract.
- Phase: implementation -> hardening.
- Contract updates:
  - Add stack-specific `.gitlab-ci.yml.tmpl` files.
  - Update installer renderer to render `.gitlab-ci.yml` from `{{validation_command}}`.
  - Update plugin self-check to require GitLab templates.
- Validation impact:
  - Run `sh plugins/harness/scripts/plugin-self-check.sh`.
  - Run one temp install per stack and verify rendered `.gitlab-ci.yml` has no placeholder tokens.
- Rejected changes:
  - No top-level plugin `hooks` manifest entry.
- Risks:
  - GitLab YAML is structurally checked but not executed against a live GitLab runner.
```

### Rejected drift

```text
delta: `docs/harness/git-hooks/pre-push` was deleted because hook validation failed.
decision: reject as drift
contract updates: restore or regenerate the selected-mode two-stage hook templates.
validation impact: rerun the selected stack command and inspect hook docs.
risks: active `.git/hooks/pre-commit` and `.git/hooks/pre-push` remain target repository files and require explicit approval to replace.
```

### Deferred dispatcher migration

```text
delta: Adopt `docs/harness/validate.sh` as a generic dispatcher.
decision: defer until README, installer, CI templates, hook generation, validators, and self-check agree on the dispatcher contract.
contract updates: none yet.
validation impact: keep the current selected stack command.
risks: adding the dispatcher early creates a second validation source of truth.
```

## Validation Commands

Use `harness-validate` after implementing an evolution. The expected stack commands are:

```text
Gradle harness validation: ./gradlew harnessValidate or gradle harnessValidate
Gradle final check: ./gradlew check or gradle check
Maven: mvn -q -f docs/harness/maven-plugin/pom.xml install && mvn -q ai.harness:harness-maven-plugin:0.1.0:validate
uv: uv run python docs/harness/uv/harness_validate.py
bun: bun run docs/harness/bun/harness-validate.ts
```

## Synchronization Checklist

- If `AGENTS.md` changes, check `CLAUDE.md` symlink or shared-contract behavior.
- If `docs/harness/manifest.json` changes, update validators and self-checks.
- If templates move, update README, installer paths, and self-check required directories.
- If stack commands change, update README, `harness-validate`, installed target `harness-validate`, CI templates, hook generation, and printed installer output.
- If generated artifact policy changes, update `docs/generated` guidance and template examples.
- If installer behavior changes, confirm ordinary harness evolution actually needs an installer change rather than a target-owned docs/template edit.

## Invariants

- The current committed harness is the active target contract.
- Harness evolution is versioned and reviewable.
- Validation must remain runnable after the evolution.
- Placeholder changes must guide target truth without inventing fake product content.
- Seed references may be replaced when the target stack or domain changes.
- CI additions and the generated pre-push hook MUST mirror the selected final check command rather than creating a second source of truth.
- Generated-artifact policy MUST distinguish source templates, generated outputs, and keep files.
- Plugin evolution MUST keep the Claude plugin manifest free of `hooks`, `agents`, `version`, and `interface` keys.
- Hook evolution MUST NOT mutate `core.hooksPath` or create harness-specific backup files.

## Pitfalls

- Do not remove validation because it is failing.
- Do not keep obsolete placeholder files as required readiness evidence.
- Do not treat chat-only instructions as harness evolution.
- Do not update templates without checking the installed docs or manifest they support.
- Do not add backward-compatibility shims for unreleased harness drafts.
- Do not treat GitHub Actions and GitLab CI as separate validation definitions; they are renderings of the same command.
- Do not edit product implementation code while performing harness evolution unless the user separately asked for that implementation.
- Do not introduce top-level `hooks/hooks.json` for this plugin.
- Do not discard validation output; diagnostic output is review evidence.
- Do not force successful exits after validation failure; classify non-fatal findings as WARN explicitly.
- Do not introduce fake placeholder files under `docs/generated/`; keep the directory empty with `.gitkeep` until a real generation step exists.
- Do not keep duplicate template roots unless README, installer, validators, and self-checks all declare which one is canonical.
- Do not edit installer scripts during ordinary evolution unless install behavior itself is the evolution target.

## Report Template

```text
delta: <summary of the change that triggered evolution>
decision: <evolve|reject as drift|defer>
contract updates:
- <path>: <required change>
validation impact: <command and expected coverage>
ci impact: <none|github|gitlab|both>
risks:
- <remaining placeholder, seed reference, or docs gap>
```

## Output Contract

Report these fields:

- `delta`: concise summary of the project change that triggered evolution.
- `decision`: evolve, reject as drift, or defer.
- `contract updates`: files or template groups that must change.
- `validation impact`: command and expected coverage.
- `ci impact`: whether GitHub Actions or GitLab CI examples must change.
- `risks`: remaining placeholders, seed references, or docs that still need target-specific content.
