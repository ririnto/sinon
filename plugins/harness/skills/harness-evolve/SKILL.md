---
name: harness-evolve
description: >-
  Reconcile an installed target-owned repository harness after real project use changes docs, templates, agents, skills, validators, generated-artifact policy, CI, hooks, or readiness gates. Use when repeated validation or review failures or committed harness drift show the target harness contract should evolve while keeping installable assets under the plugin asset package.
argument-hint: '[summary-of-delta]'
disable-model-invocation: true
allowed-tools:
  - Read
  - Grep
  - Glob
  - LS
  - Bash(git diff *)
  - Bash(git status *)
---

# Harness Evolve

Review how the installed target harness changed and produce a versioned evolution plan. This plugin skill is the visible runtime surface for harness evolution guidance; target repository files remain packaged under `skills/harness-install/assets/` before install and target-owned after copying.

This skill is report-only unless the user separately asks for implementation. Produce the evolution plan first, then use `harness-validate` after changes are implemented.

## Ownership Boundary

- This skill owns harness evolution guidance.
- Target repository agents, project skills, docs, validators, CI snippets, and hook scaffolds evolve as installable assets under `skills/harness-install/assets/`.
- Plugin-root structural agents evolve only when their harness-lifecycle advisory role changes.

## First Safe Checks

1. Read `AGENTS.md`, `docs/harness/README.md`, and any active execution-plan entry under `docs/exec-plans/` that touches the harness.

2. Inspect the user-provided delta summary and current `git diff` for harness-owned files.
3. Separate product changes from harness changes.
4. Confirm that every proposed harness change has a matching validation path or an explicit informational-only reason.
5. Check CI examples when validation commands, stack mode, or generated-artifact policy changes.

```sh
git status --short
```

```sh
git diff -- AGENTS.md CLAUDE.md ARCHITECTURE.md docs .claude .github/workflows .gitlab-ci.yml
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
    | Generated artifacts | `docs/generated/**`, generated-artifact template |

2. Decide whether the change is a legitimate evolution or a local drift.
3. Keep valid minor improvements; reject only changes that break the harness contract or product fit.
4. Update the evolution plan so docs, templates, agents, skills, validators, and `.gitkeep` placeholders stay aligned.
5. Identify the stack-specific validation command that must pass after implementation.

## Evolution Decision Table

| Situation | Decision | Required action |
| --- | --- | --- |
| Product architecture changed and docs are stale | evolve | Update `ARCHITECTURE.md`, relevant design docs, and validation expectations together. |
| A generated artifact moved or was renamed | evolve | Update generated-artifact docs and any template or CI references. |
| CI should run the same final check command on a new platform | evolve | Add or update CI template examples while preserving the selected pre-push command. |
| Validation fails because a required file was deleted accidentally | reject as drift | Restore the file or re-run installation; do not weaken the contract. |
| Placeholder content is still generic after installation | defer | Ask for target truth or create the relevant spec/design task first. |
| A seed reference no longer fits the target stack | evolve | Replace the seed reference and record why the new source is target-specific. |
| Product code changed but harness contracts still match reality | reject as drift | Report that the change is product work, not harness evolution. |
| Validator is too strict for ignored or generated files | evolve | Adjust validator rules only after confirming gitignore-aware behavior. |
| Proposed change removes validation to make a failure pass | reject as drift | Keep validation and fix the underlying contract or target content. |
| A target no longer uses an optional surface | evolve | Remove that surface only when docs, templates, validators, CI/hooks, and self-check agree it is optional or replaced. |

## Cleanup Evolution

Treat cleanup as harness evolution when a repository stops using a previously installed surface. Examples include a GitLab-only target removing GitHub Actions examples, an API-free target removing generated API docs, or a project-specific workflow retiring a seed template.

Use this sequence before recommending deletion:

1. Identify the owning surface: CI, hooks, docs, generated artifacts, agents, skills, seed references, or stack runtime.
2. Classify the artifact as required, optional seed, generated output, or target-owned runtime state.
3. Check `docs/harness/README.md`, installer assets, validators, hook/CI examples, and self-check fixtures for the same expectation.
4. If every contract marks the surface optional or replaced, propose removal plus the matching docs/validator/CI updates.
5. If any contract still requires it, reject the deletion as drift or first evolve the contract that makes it required.

Cleanup MUST NOT delete active target truth just because it is unused by the default template. It MUST leave one validation source of truth: GitHub Actions and GitLab CI are alternative renderings of the selected final check command, not independent policy definitions.

### Cleanup examples

```text
delta: Target repository runs GitLab CI only and wants to remove `.github/workflows/<tool>.yaml` from the installed repository.
decision: evolve if GitHub Actions is documented as an optional CI rendering.
contract updates: remove the GitHub Actions file and any target-facing references that assume it exists.
validation impact: selected stack command must pass, and the present `.gitlab-ci.yml` must match the generated pre-push final-check command.
risks: downstream docs may still mention GitHub pull request checks.
```

```text
delta: Plugin package should stop receiving GitHub-only CI scaffolding for this policy.
decision: evolve only when this cleanup is also intended for future installs.
contract updates: update installer templates, `docs/harness/README.md`, and self-check expectations together; this is separate from target-side cleanup of existing installs.
validation impact: selected stack command must pass and post-install CI renderings must still match generated `pre-push`.
risks: mixed-host repositories may still need optional GitHub rendering.
```

```text
delta: Target has no generated API schema and wants to remove `docs/generated/api-schema.md`.
decision: evolve only if generated-artifact policy no longer requires that artifact.
contract updates: update generated-artifact docs and any references that link to the removed output.
validation impact: selected stack command must pass without fake generated files.
risks: product docs may still link to the removed schema.
```

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
- `docs/harness/README.md` documents the selected stack validation command and CI host configuration.
- `.claude/agents/**` and `.claude/skills/**` remain self-sufficient for target repository use.
- `docs/harness/templates/**` still contain placeholders only where the template renderer or human copy step expects them.
- `.github/workflows/<tool>.yaml` and `.gitlab-ci.yml`, when present, run the selected final check command.
- `docs/generated/**` contains real generated artifacts or `.gitkeep`, not fake readiness files.
- A `docs/exec-plans/` entry records the reason, files changed, validation command, and remaining follow-up for any non-trivial evolution.

## Evolution Plan Entry

Record the evolution in a new or existing execution plan under `docs/exec-plans/` using the project's standard execution-plan template format (typically `yyyy-MM-dd-<slug>.md`). The plan SHOULD include:

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
decision: evolve only if generated-artifact docs also remove the requirement.
contract updates: update generated-artifact index and any references.
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
- Validation impact:
  - Run one temp install per stack and verify rendered `.gitlab-ci.yml` has no placeholder tokens.
- Rejected changes: none.
- Risks:
  - GitLab YAML is structurally checked but not executed against a live GitLab runner.
```

### Rejected drift

```text
delta: `docs/harness/git-hooks/pre-push` was deleted because hook validation failed.
decision: reject as drift
contract updates: restore or regenerate the selected-mode two-stage hook templates.
validation impact: rerun the selected stack command and inspect hook docs.
risks: active `pre-commit` and `pre-push` hook files remain target repository files and require explicit approval to replace.
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
Gradle: ./gradlew ktlintCheck
Maven: root=$(pwd -P); files=$(git ls-files -- "*.java" | while IFS= read -r file; do case "$file" in *,*) echo "error: Java path contains comma and cannot be represented in spotlessFiles: $file" >&2; exit 1;; esac; printf '%s/%s\n' "$root" "$file" | sed 's/[][\\.^$*+?{}()|]/\\&/g; s/^/^/; s/$/$/'; done | paste -sd, -); if [ -z "$files" ]; then ./mvnw validate; echo "spotless: no tracked Java files to check"; else ./mvnw validate -DspotlessFiles="$files"; fi
uv: uv run scripts/check.py
bun: bun run check
shell: sh scripts/check.sh
```

## Synchronization Checklist

- If `AGENTS.md` changes, check `CLAUDE.md` symlink or shared-contract behavior.
- If stack mode changes, update validators, CI examples, and hook templates.
- If templates move, update README, installer paths, and self-check required directories.
- If stack commands change, update README, `harness-validate`, the installed target check scripts (`scripts/check.py` or `scripts/check.sh`) and build-tool hook-sync wiring, CI templates, hook generation, and printed installer output.
- If generated artifact policy changes, update `docs/generated` guidance and template examples.
- If installer behavior changes, confirm ordinary harness evolution actually needs an installer change rather than a target-owned docs/template edit.

## Invariants

- The current committed harness is the active target contract.
- Harness evolution is versioned and reviewable.
- Validation must remain runnable after the evolution.
- Placeholder changes must guide target truth without inventing fake product content.
- Seed references may be replaced when the target stack or domain changes.
- CI additions and the generated pre-push hook MUST mirror the selected stack validation command rather than creating a second source of truth.
- Generated-artifact policy MUST distinguish source templates, generated outputs, and keep files.
- Plugin evolution MUST keep the Claude plugin manifest free of `hooks`, `agents`, `version`, and `interface` keys.
- Hook evolution MUST NOT mutate `core.hooksPath` or create harness-specific backup files.

## Pitfalls

- Do not remove validation because it is failing.
- Do not keep obsolete placeholder files as required readiness evidence.
- Do not treat chat-only instructions as harness evolution.
- Do not update templates without checking the installed docs they support.
- Do not version the harness; treat the current committed validators as the only authoritative shape. Do not add shims, deprecation paths, or "previous schema" handling — replace, don't layer.
- Do not treat GitHub Actions and GitLab CI as separate validation definitions; they are renderings of the same command.
- Do not edit product implementation code while performing harness evolution unless the user separately asked for that implementation.
- Do not introduce top-level plugin manifest `hooks` or `agents` keys.
- Do not discard validation output; diagnostic output is review evidence.
- Do not force successful exits after validation failure; classify non-fatal findings as WARN explicitly.
- Do not introduce fake placeholder files under `docs/generated/`; keep the directory empty with `.gitkeep` until a real generation step exists.
- Do not keep duplicate template roots unless README and installer declare which one is canonical.
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
