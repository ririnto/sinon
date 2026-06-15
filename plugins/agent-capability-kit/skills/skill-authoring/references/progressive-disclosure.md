---
name: progressive-disclosure
description: Decide whether skill material belongs in `SKILL.md`, references/, assets/, or scripts/.
---

# Progressive Disclosure

Open this file when `SKILL.md` is getting crowded or when you are unsure whether material belongs in the entrypoint or a support file.

## Core rule

`SKILL.md` is the activation entrypoint.
Keep every ordinary-path instruction there.
Move only optional depth into support files.

## Keep in `SKILL.md`

- Activation-time purpose and scope.
- Frontmatter rules and display-title placement when the skill authors skills.
- First safe checks.
- Main workflow and required decisions.
- Required validation steps.
- Default file layout.
- Output shape.
- Always-on guardrails.
- Short representative examples.

## Move to `references/`

- Host-specific deviations.
- Extended examples.
- Troubleshooting after the ordinary workflow fails.
- Compatibility details that do not apply to every run.
- Decision material for a named blocker.

Each reference must say when to open it and must not be required before `SKILL.md` can be used.

## Move to `assets/`

- Copyable templates.
- Starter configuration files.
- Schemas and fixtures.
- Reusable checklists.
- Example artifacts that should be copied rather than read as prose.

## Move to `scripts/`

- Deterministic validators.
- File generators.
- Static checks.
- Mechanical transforms.
- Report formatters.

Do not move interactive workflows, web-required setup, or host-specific wrappers into scripts for the ordinary path.

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
