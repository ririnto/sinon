---
name: your-skill-name
description: >-
  Write the imperative capability statement here.
  Use when the trigger clause adds distinct task, artifact, system, or user-intent vocabulary.
---

# Your Skill Name

State the outcome in one or two lines.
Keep the ordinary path usable from this file alone.

## Operating rules

- Keep skill frontmatter limited to `name` and `description`.
- Put the display title in this H1, not in frontmatter.
- Put version, source, baseline, owner, or license notes in the body only when ordinary use needs them.
- Use support files only for named blockers, copyable artifacts, or deterministic checks.
- Do not require web access, host-specific wrappers, or external validators for the ordinary path.

## First safe checks

1. Confirm this directory is the skill directory and `SKILL.md` is the primary entrypoint.
2. Read relevant local rules and existing support files.
3. Verify that no reference file is needed to follow the ordinary path.
4. Keep support files optional and additive.

## Procedure

1. Read the relevant inputs.
2. Plan the ordinary-path steps that belong in `SKILL.md`.
3. Draft the main instructions, defaults, and decisions.
4. Validate the draft against the intended trigger and file split.
5. Revise weak spots before finalizing the output.

## Edge cases

- If inputs are missing, state what is missing and stop.
- If the scope expands into adjacent jobs, narrow it before adding more instructions.
- If validation fails, report the failure and the blocking condition.
- If the result is ambiguous, name the ambiguity and choose the safest bounded path only when the user allowed that choice.

## Output contract

Return:

1. The main artifact.
2. Any changed files or paths.
3. Validation results.
4. Explicit remaining risks or blockers.

## Optional progress checklist

- Scope is one coherent job.
- `SKILL.md` covers the ordinary path on its own.
- Support files are additive only.
- Description states what the skill does and when to use it.
- Final validation passed.

## Optional gotchas

- Do not move always-needed guidance into `references/`.
- Do not make a helper script mandatory for the ordinary path.
- Do not widen the description until unrelated prompts also match.

## Optional support-file pointers

- `references/REFERENCE.md` - Open only when a named blocker or deeper branch is required.
- `assets/TEMPLATE.ext` - Copy when creating the target artifact.
- `scripts/validate.py` - Run when verification is better expressed as code.
