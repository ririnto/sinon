---
name: your-skill-name
description: [Imperative capability statement: e.g., Write, Author, Review, Build, Triage]. Use when the task, inputs, systems, file types, or user intent clearly matches this workflow.
# Optional:
# license: Proprietary. LICENSE has complete terms
# compatibility: Requires python3 and jq
# metadata:
#   owner: your-team
#   maturity: draft
---

# Your Skill Name

State the outcome in one or two lines.

## Operating rules

- Keep the common path self-sufficient in this file.
- Keep the workflow focused on one coherent job.
- Use support files only for named blockers, copyable artifacts, or deterministic checks.
- Do not require web access, host-specific behavior, or external validation services for the ordinary path.

## First safe checks

Before you write the rest of the skill:

1. Confirm the skill stays in one flat directory with `SKILL.md` as the primary entry point.
2. Read the template, the validation checklist, and the intended `SKILL.md` together.
3. Verify that no reference file is needed to follow the ordinary path.
4. Keep support files optional and additive rather than chained prerequisites.

## Procedure

1. Read the relevant inputs and confirm the scope.
2. Plan the ordinary-path steps and decide what stays in `SKILL.md`.
3. Draft the main instructions and defaults.
4. Validate the draft against the intended trigger and file split.
5. Revise weak spots before finalizing the output.

## Edge cases

- If inputs are missing, state what is missing and stop.
- If the scope expands into adjacent jobs, narrow it before adding more instructions.
- If validation fails, report the failure and the blocking condition.
- If the result is ambiguous, call out the ambiguity and the safest interpretation.

## Output contract

Return:

1. The main artifact
2. Any changed files
3. Validation results
4. Explicit remaining risks or blockers

## Optional progress checklist

- [ ] Scope is one coherent job
- [ ] `SKILL.md` covers the ordinary path on its own
- [ ] Support files are additive only
- [ ] Description states both what and when
- [ ] Final validation passed

## Optional gotchas

- Do not move always-needed guidance into `references/`.
- Do not make a helper script mandatory for the ordinary path.
- Do not widen the description until unrelated prompts also match.

## Optional support-file pointers

- `references/REFERENCE.md` - Open only when a named blocker or deeper branch is required.
- `assets/TEMPLATE.ext` - Copy when creating the target artifact.
- `scripts/validate.py` - Run when verification is better expressed as code.
