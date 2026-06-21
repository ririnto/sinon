---
name: harness-evolve
description: >-
  Reconcile an installed target-owned repository harness after real project use
  changes docs, templates, agents, skills, validators, generated-artifact
  policy, CI, hooks, or readiness gates. Use when repeated validation or review
  failures or committed harness drift show the target harness contract should
  evolve while keeping installable assets under the plugin asset package.
---

# Harness Evolve

Review how the installed target harness changed and produce a versioned evolution plan.
This plugin skill is the visible runtime surface for harness evolution guidance.
Target repository files remain packaged under `skills/harness-install/assets/`
before install and become target-owned after copying.

This skill is report-only unless the user separately asks for implementation.
Produce the evolution plan first, then use `harness-validate` after changes are implemented.

## Ownership Boundary

- This skill owns harness evolution guidance.
- Target repository assets evolve under `skills/harness-install/assets/`.
  - Includes agents, project skills, docs, validators, CI files, and hook scaffolds.
- Plugin-root structural agents evolve only when their harness-lifecycle advisory role changes.

## First Safe Checks

1. Read the harness context.
   - Read `WORKFLOW.md`.
   - Read any active execution-plan entry under `docs/exec-plans/` that touches the harness.
   - Inspect `CLAUDE.md` only when the pointer file itself changed.
2. Inspect the user-provided delta summary and current `git diff` for harness-owned files.
   - Run:

     ```sh
     git status --short
     ```

   - Run:

     ```sh
     git diff -- AGENTS.md CLAUDE.md ARCHITECTURE.md docs .claude .github/workflows .gitlab-ci.yml
     ```

3. Separate product changes from harness changes.
4. Confirm that every proposed harness change has either a validation path or an explicit informational-only reason.
5. Check CI examples when validation commands, stack mode, or generated-artifact policy changes.
   - Use the stack validation command from `WORKFLOW.md`.
   - Use the installed workflow command when it names one.

## Workflow

1. Classify the change.

    | Change type | Typical files |
    | --- | --- |
    | Context contract | `AGENTS.md`, `CLAUDE.md`, `ARCHITECTURE.md` |
    | Documentation structure | `docs/**`, `docs/templates/docs/**` |
    | Work surface | `.claude/agents/**`, `.claude/skills/**` |
    | Validation surface | stack hook assets, CI files, hook templates |
    | Generated artifacts | `docs/generated/**`, generated-artifact template |

2. Decide whether the change is a legitimate evolution or a local drift.
3. Keep valid minor improvements.
   - Reject only changes that break the harness contract or product fit.
4. Update the evolution plan so related surfaces stay aligned.
   - Include docs, templates, agents, skills, validators, and `.gitkeep` placeholders.
5. Identify the stack-specific validation command that must pass after implementation.

## Evolution Decision Table

| Situation | Decision | Required action |
| --- | --- | --- |
| Product architecture changed and docs are stale | evolve | Update `ARCHITECTURE.md` and relevant design docs. |
| | | Update validation expectations in the same change. |
| A generated artifact moved or was renamed | evolve | Update generated-artifact docs and any installed CI references. |
| CI should run the same final check command on a new platform | evolve | Add or update install-time CI assets. |
| | | Preserve the selected pre-push command. |
| Validation fails because a required file is missing unexpectedly | reject as drift | Restore the file or re-run installation. |
| | | Fix the underlying contract or target content. |
| Placeholder content is still generic after installation | defer | Ask for target truth. |
| | | Or create the relevant spec/design task first. |
| A seed reference no longer fits the target stack | evolve | Replace the seed reference. |
| | | Record why the new source is target-specific. |
| Product code changed, but harness contracts still match reality | reject as drift | Report product-work scope. |
| Validator is too strict for ignored or generated files | evolve | Adjust validator rules. |
| | | Confirm gitignore-aware behavior first. |
| Proposed change removes validation to make a failure pass | reject as drift | Keep validation. |
| | | Fix the underlying contract or target content. |
| A target retires an optional surface | evolve | Remove the optional surface. |
| | | Update docs, repository templates, validators, CI, hooks, and self-checks together. |

## Cleanup Evolution

Treat cleanup as harness evolution when a repository stops using a previously installed surface.
Examples include a GitLab-only target removing GitHub Actions examples.
Other examples include an API-free target removing generated API docs or a project-specific workflow retiring a seed template.

Use this sequence before recommending deletion:

1. Identify the owning surface.
   - The owner may be CI, hooks, docs, generated artifacts, agents, skills, seed references, or stack runtime.
2. Classify the artifact as required, optional seed, generated output, or target-owned runtime state.
3. Check the same expectation across `WORKFLOW.md`, installer assets, validators, hook/CI assets, and self-check fixtures.
4. If every contract marks the surface optional or replaced, propose removal.
   - Include the matching docs, validator, and CI updates.
5. If any contract still requires it, reject the deletion as drift.
   - Alternatively, first evolve the contract that makes it required.

Cleanup preserves active target truth even when the default template stops using that surface.
It MUST leave one validation source of truth.
GitHub Actions and GitLab CI are host-specific files for the selected final check command.

### Cleanup examples

```text
delta: Target repository runs GitLab CI only and wants to remove `.github/workflows/<tool>.yaml` from the installed repository.
decision: evolve if GitHub Actions is documented as an optional CI asset.
contract updates: remove the GitHub Actions file and any target-facing references that assume it exists.
validation impact: selected stack command must pass.
The present `.gitlab-ci.yml` must match the installed pre-push final-check command.
risks: downstream docs may still mention GitHub pull request checks.
```

```text
delta: Plugin package should stop receiving GitHub-only CI scaffolding for this policy.
decision: evolve only when this cleanup is also intended for future installs.
contract updates: update installer assets, `WORKFLOW.md`, and self-check expectations together.
This is separate from target-side cleanup of existing installs.
validation impact: selected stack command must pass and post-install CI assets must still match installed `pre-push`.
risks: mixed-host repositories may still need optional GitHub Actions assets.
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

- `AGENTS.md` describes the target contract.
- `CLAUDE.md` remains a pointer document that imports `AGENTS.md`.
- `WORKFLOW.md` names the current validation command.
- `WORKFLOW.md` documents the selected stack validation command and CI host configuration.
- `.claude/agents/**` and `.claude/skills/**` remain self-sufficient for target repository use.
- `docs/templates/**` still contain placeholders only where the human copy step expects them.
- `.github/workflows/<tool>.yaml` and `.gitlab-ci.yml`, when present, run the selected final check command.
- `docs/generated/**` contains real generated artifacts or `.gitkeep`.
- A `docs/exec-plans/` entry records non-trivial evolution:
  - Reason.
  - Files changed.
  - Validation command.
  - Remaining follow-up.

## Evolution Plan Entry

Record the evolution in a new or existing execution plan under `docs/exec-plans/`.
Use the project's standard execution-plan template format.
Name new plans `yyyy-MM-dd-<slug>.md`.
The plan SHOULD include:

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
contract updates: add `.gitlab-ci.yml` harness job that runs the same installed pre-push final check command.
validation impact: local harness validation stays unchanged.
CI mirrors the installed pre-push final check command.
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

- Delta: GitLab CI files added for all stack assets.
- Decision: Evolve harness contract.
- Phase: implementation -> hardening.
- Contract updates:
  - Add stack-specific `.gitlab-ci.yml` asset files.
  - Update installer selection so `--ci-host gitlab` installs `.gitlab-ci.yml`.
- Validation impact:
  - Run one temp install per stack and verify `.gitlab-ci.yml` appears for selected GitLab targets.
- Rejected changes: none.
- Risks:
  - GitLab YAML receives structural checks.
  - Live runner execution remains a release-environment check.
```

### Rejected drift

```text
delta: a selected-stack hook template was deleted because hook validation failed.
decision: reject as drift
contract updates: restore or regenerate the selected-mode two-stage hook templates.
validation impact: rerun the selected stack command and inspect hook docs.
risks: active `pre-commit` and `pre-push` hook files remain target repository files and require explicit approval to replace.
```

### Deferred dispatcher migration

```text
delta: Adopt `docs/validate.sh` as a generic dispatcher.
decision: defer until README, installer, CI files, hook assets, validators, and self-check agree on the dispatcher contract.
contract updates: none yet.
validation impact: keep the current selected stack command.
risks: adding the dispatcher early creates a second validation source of truth.
```

## Validation Commands

Use `harness-validate` after implementing an evolution.
The expected stack commands are:

- Gradle:

  ```sh
  ./gradlew ktlintCheck
  ```

- Maven:

  ```sh
  root=$(pwd -P)
  files=$(
      git ls-files -- "*.java" | while IFS= read -r file; do
          case "$file" in
              *,*)
                  echo "error: Java path contains comma and cannot be represented in spotlessFiles: $file" >&2
                  exit 1
                  ;;
          esac
          printf '%s/%s\n' "$root" "$file" | sed 's/[][\\.^$*+?{}()|]/\\&/g; s/^/^/; s/$/$/'
      done | paste -sd, -
  )
  if [ -z "$files" ]; then
      ./mvnw validate
      echo "spotless: no tracked Java files to check"
  else
      ./mvnw validate -DspotlessFiles="$files"
  fi
  ```

- uv:

  ```sh
  uv run scripts/check.py
  ```

- bun:

  ```sh
  bun run check
  ```

- shell:

  ```sh
  sh scripts/check.sh
  ```

## Synchronization Checklist

- If `AGENTS.md` changes, confirm `CLAUDE.md` still imports `AGENTS.md`.
- If stack mode changes, update validators, CI files, and hook templates.
- If templates move, update README and installer paths.
  - Update self-check required directories.
- If stack validation behavior changes, update every validation surface.
  - Include README and `harness-validate`.
  - Include installed target check scripts, build-tool hook-sync wiring, CI files, hook assets, and printed installer output.
- If generated artifact policy changes, update `docs/generated` guidance and template examples.
- If installer behavior changes, confirm whether the evolution needs an installer change.
  - Prefer a target-owned docs/template edit when install behavior itself does not change.

## Invariants

- The current committed harness is the active target contract.
- Harness evolution is versioned and reviewable.
- Validation must remain runnable after the evolution.
- Placeholder changes must guide target truth without inventing fake product content.
- Seed references may be replaced when the target stack or domain changes.
- CI additions and the installed pre-push hook MUST mirror the selected stack validation command as one source of truth.
- Generated-artifact policy MUST distinguish source templates, generated outputs, and keep files.
- Plugin evolution MUST keep `interface` out of the Claude plugin manifest.
- Plugin evolution SHOULD omit manifest `version` unless the plugin has a semver release policy.
- Plugin evolution SHOULD keep default runtime files and directories out of the manifest when the default path is the only value.
  - Examples:
    - `hooks/hooks.json`, `.mcp.json`, `.lsp.json`, and `settings.json`.
    - `output-styles/`, `themes/`, and `monitors/monitors.json`.
- Plugin evolution SHOULD keep default plugin-root component locations out of the manifest.
  - Use component keys only for custom paths or inline configuration.
- Hook evolution preserves target-owned `core.hooksPath` and records any required hook migration as explicit target policy.

## Operating Checks

- Keep validation in place and fix the failing contract or target content.
- Use target truth as readiness evidence.
- Record harness evolution in versioned files.
- Check installed docs before changing the templates that support them.
- Treat the current committed validators as the authoritative shape.
  - Replace obsolete forms directly.
- Keep GitHub Actions and GitLab CI aligned to the same validation command.
- Keep product implementation work separate from harness evolution when the user asks only for harness work.
- Keep plugin manifest surfaces within the supported schema.
- Preserve validation output as review evidence.
- Classify non-fatal validation findings as WARN.
- Keep `docs/generated/` empty with `.gitkeep` until a real generation step exists.
- Keep one canonical template root declared by README and installer.
- Edit installer scripts when install behavior itself is the evolution target.

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
