---
name: harness-orchestrate
description: >-
  Coordinate target repository work through the installed harness lifecycle. Use this skill when planning or sequencing work that must read CLAUDE.md, update project context, delegate implementation/review, and finish with the stack-specific harness validator.
---

# Harness Orchestrate

Plan and coordinate work inside this target repository using the installed harness. This target skill is local to the repository after installation; plugin skills remain responsible for installing, validating, or evolving the harness package itself.

## First Safe Checks

1. Read `CLAUDE.md`, `ARCHITECTURE.md`, `docs/harness/README.md`, and the relevant `docs/**` file for the task domain.
2. Identify whether the task is implementation, documentation, validation, review, or harness evolution.
3. Confirm the stack-specific validation command from `docs/harness/README.md`.

## Workflow

1. Define the goal, acceptance gate, affected docs, and validation command.
2. Ensure missing product, design, architecture, or execution-plan context is created before implementation begins.
3. Assign bounded work to target agents only when their role matches the task.
4. Keep plugin-owned installation decisions separate from target-owned project decisions.
5. Require implementation evidence, review findings, and stack validation before declaring completion.

## Decisions

| Situation | Action |
| --- | --- |
| Task lacks product or acceptance criteria | Update the relevant `docs/product-specs/**` or `docs/exec-plans/**` first. |
| Task changes harness contracts | Open or update `docs/exec-plans/active/yyyy-MM-dd-<slug>.md` and run the stack validator. |
| Task changes generated outputs | Require source command, inputs, freshness, and regeneration trigger. |
| Task asks for hook activation | Confirm explicit user intent before changing Git hook behavior. |

## Invariants

- `CLAUDE.md` is the primary target repository contract.
- The installed harness is target-owned and may evolve with the repository.
- Placeholder docs are not project truth until populated with target-specific evidence.
- Validation runs through the target stack, not through ad hoc inspection alone.

## Output Contract

Report:

- `plan`: ordered work sequence and owners.
- `context`: docs or contracts read or missing.
- `validation`: command required before completion.
- `risks`: placeholders, seed references, or unresolved decisions.
