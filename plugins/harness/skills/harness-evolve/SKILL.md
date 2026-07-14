---
name: harness-evolve
description: >-
  Assess and plan installed-target or plugin-default Harness changes after real project use. Use when harness docs, skills, templates, validation, CI, hooks, or generated-artifact policy need a deliberate update.
---

# Harness Evolve

Produce an evolution report by default. Implement only when the user separately requests it.

## First Safe Checks

1. Classify the request as installed-target or plugin-default evolution.
2. For an installed target, read `.harness/install-record.json` and inspect the target diff. For a plugin default, inspect the packaged assets, asset manifest, and repository diff.
3. Separate product changes from harness-contract changes.
4. Identify the validation surface. Use the recorded canonical command for an installed target and repository checks for a plugin default.

## Procedure

1. Classify the target.

    | Track | Change surface | Effect |
    | --- | --- | --- |
    | Installed target | Copied files such as `AGENTS.md`, docs, `.claude/skills`, CI, and hooks | One repository. |
    | Plugin default | `skills/harness-install/assets/` and plugin packaging | Future installs; existing targets need an explicit refresh. |

2. Decide `evolve`, `reject as drift`, or `defer`.
3. For legitimate one-target divergence, plan `harness-install --adopt <path>` only when it must remain target-owned. Adoption preserves bytes and requires a complete record.
4. List each contract surface that must stay aligned: docs, templates, skills, stack validation, CI, hooks, or generated-artifact guidance.
5. Record validation after implementation. For an installed target, CI and `pre-commit`, when present, MUST use the exact recorded canonical command; `pre-push` may be stricter. For a plugin default, record the repository checks and representative install smoke.
6. For nontrivial work, add or update the relevant repository's execution-plan record using its local format.

## Decisions

- Evolve stale architecture, templates, validation rules, or CI that reflect a real lasting project change.
- Reject accidental deletion, command weakening, or edits that only mask a failing contract.
- Defer generic placeholders until the target owner provides real project facts.
- Treat a plugin-default change as a future-install change. Do not use it to patch a single target.
- Treat a target `WORKFLOW.md` as an optional local constraint unless the target contract requires it for the requested change.

## Output Contract

Report:

- `delta`.
- `decision`: evolve, reject as drift, or defer.
- `track`: installed target or plugin default.
- `contract updates`: paths or asset groups.
- `validation impact`: exact command and coverage.
- `ci impact`: none, GitHub, GitLab, or both.
- `risks`: remaining target facts, migrations, or unresolved policy.

## Support Files

- Read installed docs, templates, or `WORKFLOW.md` only when they govern a surface in the proposed change.
- Use `harness-validate` after implementation; this skill does not replace validation.
