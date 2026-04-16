---
title: Skill Progressive Disclosure Reference
description: >-
  Reference for deciding what belongs in SKILL.md, what belongs in references, and when scripts or assets are justified.
---

Use this reference when the blocker is content placement rather than content quality. This file should be sufficient on its own to decide whether material belongs in `SKILL.md`, `references/`, `scripts/`, or `assets/`.

## Default Placement Rules

Keep these in `SKILL.md`:

- activation surface and ordinary use cases
- the common-case workflow
- first safe commands or code shape
- canonical templates and output shapes
- core validation checks
- stable invariants, pitfalls, and scope boundaries

Move these to `references/`:

- edge-case decision material
- extended examples that are too long for the common path
- portability notes across runtimes or vendors
- deeper comparisons that only matter after a blocker appears

Add `scripts/` only when the workflow needs executable helpers. Add `assets/` only when templates or other files are consumed directly during execution.

## Fast Placement Test

Ask these questions in order:

1. Can a maintainer complete the ordinary task without this content?
   - If no, keep it in `SKILL.md`.
2. Does the content resolve one specific blocker after activation?
   - If yes, move it to `references/`.
3. Does the workflow depend on running code or consuming a file artifact?
   - If yes, consider `scripts/` or `assets/`.
4. Is the content duplicated from `SKILL.md` only to reduce file size?
   - If yes, move it back into `SKILL.md`.

## Broken vs Correct Splits

Broken split:

```text
SKILL.md
references/
  common-workflow.md
  core-templates.md
```

Why it fails: the common path is no longer self-sufficient.

Correct split:

```text
SKILL.md
references/
  routing-calibration.md
  vendor-deltas.md
```

Why it works: the ordinary path stays in `SKILL.md`, while deeper blockers move out.

## Reference Completeness Rule

Each reference file should open with:

- the blocker that justifies reading it
- a statement that the file is sufficient on its own for that blocker
- concrete examples, commands, or patterns tied to the blocker

If two reference files are almost always opened together to finish one job, merge them unless the split clearly reduces scanning cost.

## Review Questions

- Would removing the references still leave a usable common path?
- Is any reference required for an ordinary authoring or review task?
- Does each reference correspond to one blocker rather than one broad topic?
- Does each reference add new material instead of repeating canonical templates?
- Are scripts and assets present only because the workflow needs them?
