---
name: harness-evolve
description: >-
  Assess and plan installed-target or plugin-default Harness changes after real project use.
  Use when harness docs, skills, templates, validation, CI, hooks, or generated-artifact policy need a deliberate update.
---

# Harness Evolve

Produce an evolution report by default. Implement only when the user separately requests it.

## First Safe Checks

1. Classify the request as installed-target or plugin-default evolution.
2. For an installed target, inspect the target files and diff directly.
   For a plugin default, inspect the packaged assets and repository diff.
3. Separate product changes from harness-contract changes.
4. Identify the validation surface.
   Use the target repository's canonical check command for an installed target and repository checks for a plugin default.

## Procedure

1. Classify the target.

   | Track | Change surface | Effect |
   | --- | --- | --- |
   | Installed target | Copied files such as repository rules, docs, `.claude/skills`, CI, and hooks | One repository. |
   | Plugin default | `skills/harness-install/assets/` and plugin packaging | Future installs require an explicit refresh for existing targets. |

2. Decide `evolve`, `reject as drift`, or `defer`.
3. For legitimate one-target divergence, plan a bounded bundle refresh that preserves target-owned files and inspect the resulting diff.
4. List each contract surface that must stay aligned: docs, templates, skills, stack validation, CI, hooks, or generated-artifact guidance.
5. Run validation after implementation.
   For an installed target, use the selected tool guidance to identify and run its validation surface.
   For a plugin default, run the repository checks and representative install smoke.
6. For nontrivial work, add or update the relevant repository's execution-plan record using its local format.

## Decisions

- Evolve stale architecture, templates, validation rules, or CI that reflect a real lasting project change.
- Reject accidental deletion, command weakening, or edits that only mask a failing contract.
- Defer generic placeholders until the target owner provides real project facts.
- Treat a plugin-default change as a future-install change. Do not use it to patch a single target.

## Output Contract

Report:

- `delta`.
- `decision`: evolve, reject as drift, or defer.
- `track`: installed target or plugin default.
- `contract updates`: paths or asset groups.
- `validation impact`: exact command and coverage.
- `ci impact`: none, GitHub, GitLab, or both.
- `risks`: remaining target facts or unresolved policy.

## Support Files

- Read installed bundle resources only when they govern a surface in the proposed change.
- Use the selected tool's documented validation after implementation.
  This skill does not replace validation.
