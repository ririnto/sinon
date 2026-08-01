---
name: progressive-disclosure
description: Decide whether skill material belongs in `SKILL.md`, references/, assets/, or scripts/.
---

# Progressive Disclosure

Open this file when `SKILL.md` is getting crowded or when you are unsure whether material belongs in the entrypoint or a support file.

Keep ordinary workflow, required decisions, and output contracts in the entrypoint.
Move additive depth to `references/`, `assets/`, or `scripts/`.
This file holds only the split-check questions and split-shape examples.

## Split check

Ask these questions before moving material out of `SKILL.md`:

1. Is this needed on most activations?
2. Is this part of the first safe path?
3. Is this a required decision or output shape?
4. Would the skill fail without this section?

If any answer is yes, keep the material in `SKILL.md`.

## Good split

```text
SKILL.md -> ordinary workflow, defaults, invariants, output contract
references/context-budget.md -> scope repair guidance
assets/template.md -> starter skeleton
scripts/validate.sh -> deterministic checks
```

## Bad split

```text
SKILL.md -> short routing stub
references/workflow.md -> required procedure
references/output.md -> required output shape
```

## Authoring rule

If a section is mostly reference material and not immediate action logic, move it out of `SKILL.md`.
If the section is needed for the ordinary path every time the skill activates, keep it in `SKILL.md`.
