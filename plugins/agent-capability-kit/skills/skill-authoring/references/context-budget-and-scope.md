---
name: context-budget-and-scope
description: Decide whether an Agent Skill is too broad, too narrow, or carrying the wrong depth in `SKILL.md`.
---

# Context Budget And Scope

Open this file when a skill feels too broad, `SKILL.md` keeps growing, or you are unsure whether one skill should be split into several coherent units.

Keep ordinary workflow, required decisions, and output contracts in the entrypoint.
Move additive depth to support files.
This file holds only the broad/narrow scope signals and the scope-repair prompts.

## Core rule

One skill should cover one job that can be described in one sentence and executed from one ordinary-path `SKILL.md`.

## Signals that scope is too broad

- The description needs several verbs joined with `and` or `or`.
- The main procedure branches early into unrelated workflows.
- Examples serve different audiences, file types, or output contracts.
- `SKILL.md` keeps gaining exception lists to handle nearby tasks.
- The output contract changes depending on which branch the user chooses.

## Signals that scope is too narrow

- Split skills would share nearly the same common path.
- Differences are mostly host names, vendor wrappers, or small command variants.
- References would be empty because every file repeats the same workflow.
- Users would usually need several sibling skills for one coherent job.

## Scope repair

Turn this:

```text
Write and validate API, CLI, and deployment skills.
```

Into this:

```text
Write or refactor one deployment-oriented skill for offline use.
```

Move additive depth out of `SKILL.md`, but keep the common path in the entrypoint.

## Merge siblings when the job is still one unit

If two candidate skills differ only by host, vendor, or small command variants, keep one skill and move the deltas into focused references.

## Scope check prompts

1. Can the job be explained in one sentence without `and/or` sprawl?
2. Would most activations follow the same first five steps?
3. Does the output contract stay stable across the normal workflow?
4. Could a user succeed from `SKILL.md` alone?

If any answer is no, fix the boundary before adding more content.
