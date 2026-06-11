---
name: skill-authoring
description: >-
  Write or refactor a cross-platform Agent Skill. Use when the skill must stay
  self-sufficient in SKILL.md, easy to trigger from its description, and usable
  offline without mandatory external tooling.
---

# Skill Authoring

Write or refactor one Agent Skill so `SKILL.md` is the activation entrypoint, the ordinary path is self-sufficient, and support files remain optional additive depth.

## Quickstart

1. Read the target skill directory, or optionally copy `assets/skill-template.md` when a new skill needs a starter skeleton.
2. Name the one coherent job the skill owns before writing instructions.
3. Write or revise `SKILL.md` first, including activation surface, ordinary workflow, decisions, edge cases, and output contract.
4. Add `references/`, `assets/`, or `scripts/` only for optional depth, copyable artifacts, or deterministic helpers.
5. Validate the result; optionally use `assets/validation-checklist.md` as a final verification pass.

## Operating rules

- Keep the ordinary path usable from `SKILL.md` alone, even offline.
- Keep skill frontmatter limited to `name` and `description`.
- Put the display title in the first H1 heading, not in frontmatter.
- Put version, baseline, source, owner, license, and official-documentation notes in the body only when the ordinary path needs them.
- Treat `references/`, `assets/`, and `scripts/` as optional support files, not prerequisites.
- Verify host, API, command, or library facts against official documentation when available; record blockers instead of inventing facts.
- Do not add scoring rubrics, generic background essays, or adjacent-domain handoffs.

## First safe checks

1. Confirm the target directory is the skill directory and that `SKILL.md` exists or will be created there.
2. Read `SKILL.md`, existing support files, and any local plugin rules that govern the skill.
3. Check that no support file is required before an agent can perform the ordinary workflow.
4. Check that the skill scope can be stated as one job without `and/or` sprawl.
5. Check that every support-file pointer names the condition for opening that file.

## Directory model

Use a flat skill directory unless the existing host requires otherwise.

```text
skill-name/
|-- SKILL.md
|-- references/
|-- assets/
`-- scripts/
```

- `SKILL.md`: activation-time instructions, ordinary workflow, key decisions, edge cases, output contract, and short support-file index.
- `references/`: optional additive depth for named blockers, host variants, troubleshooting branches, or extended examples.
- `assets/`: copyable templates, starter files, schemas, and examples.
- `scripts/`: deterministic non-interactive helper code when code is safer than prose.

## Frontmatter contract

Required fields:

- `name`
- `description`

Do not add `title`, `metadata`, `owner`, `license`, `version`, `source`, `officialDocs`, URLs, tool allowlists, argument hints, or baseline fields to skill frontmatter. If the information matters during ordinary use, place it under an H2 section in the body.

### `name`

- Use only lowercase letters, numbers, and hyphens.
- Keep it between 1 and 64 characters.
- Do not use leading, trailing, or consecutive hyphens.
- Match the skill directory basename exactly.

### `description`

- Keep it between 1 and 1024 characters.
- Open with an imperative capability statement that names what the skill does.
- Add a `Use when ...` trigger clause only when it contributes distinct task, artifact, system, timing, or user-intent vocabulary.
- Include likely prompts, file types, systems, or goals without summarizing the workflow.
- Keep it valid outside one host product unless the skill is intentionally host-specific.

Default pattern:

```text
[Imperative capability]. Use when [distinct task, inputs, systems, file types, or user intent keywords not already covered].
```

Strong example:

```text
Draft release automation runbooks and rollback notes. Use when preparing deployment procedures, CI release steps, or operational handoff docs.
```

Valid capability-only example:

```text
Review Markdown documents for structure, headings, and missing sections.
```

Weak example:

```text
Helps with releases.
```

Run an offline trigger test before keeping a description:

1. Hide the skill name.
2. Read only the `description`.
3. Ask whether another engineer would load it for intended prompts and avoid it for nearby prompts.
4. Tighten or widen the wording until both answers are yes.

Use `assets/description-patterns.md` or `references/description-design.md` only when the description is still vague, too broad, or hard to trigger.

## YAML scalar style

- Use plain or double-quoted scalars for short readable single-line values.
- Use folded block style with strip chomping, `>-`, only for one logical string that is long enough to need physical wrapping.
- Use literal block style with strip chomping, `|-`, only when line breaks are semantic.
- Do not use `>-` just because a field is a string.

Example:

```yaml
name: release-runbook
description: >-
  Draft release automation runbooks and rollback notes. Use when preparing
  deployment procedures, CI release steps, or operational handoff docs.
```

## Writing procedure

1. Onboard current state.
   Read the skill directory, local rules, existing support files, and user request. For new skills, copy `assets/skill-template.md`.
2. Define the coherent unit.
   Write one sentence for the job this skill owns. Remove adjacent jobs, optional branches, and host-specific sprawl from that sentence.
3. Plan the file split.
   Put always-needed guidance in `SKILL.md`, copyable artifacts in `assets/`, blocker-specific depth in `references/`, and deterministic helper code in `scripts/`.
4. Draft `SKILL.md` first.
   Start with outcome, operating rules, first safe checks, numbered procedure, edge cases, output contract, and a short support-file index.
5. Validate the ordinary path.
   Confirm an agent can perform the common task from `SKILL.md` alone and that every support file is optional.
6. Revise for trigger and scope.
   Check the description trigger, fold always-needed reference content back into `SKILL.md`, and move additive catalogs out of the main file.
7. Finish with the checklist.
   Use `assets/validation-checklist.md` as the final pass and fix each finding or report the blocker.

## `SKILL.md` body contract

An ordinary-path `SKILL.md` should contain:

1. A first H1 display title.
2. The outcome the skill produces.
3. Operating rules or invariants.
4. First safe checks.
5. A numbered procedure.
6. Edge cases and ambiguity handling.
7. Format-critical output shapes.
8. Brief support-file pointers indexed by concrete blocker or job.

Prefer imperative wording, concrete paths, copy-adaptable examples, and explicit commands. Keep tutorials, large examples, compatibility matrices, and troubleshooting branches in support files when they are not required on most activations.

## Placement rules

Keep content in `SKILL.md` when the agent needs it immediately after activation:

- Main workflow
- Required decisions
- Default file layout
- Output shape
- Always-on guardrails
- Short representative examples

Move content to `references/` only when it is additive rather than mandatory:

- Host-specific deviations
- Extended examples
- Troubleshooting after the main workflow fails
- Compatibility details that do not apply to every run

Move content to `assets/` when the best form is a copyable artifact:

- Markdown skeletons
- Starter configuration
- Sample JSON or YAML files
- Reusable checklists

Move content to `scripts/` when a repeated deterministic step is safer as code than prose:

- Validation helpers
- Packaging helpers
- Static checks
- Report generation

Open `references/progressive-disclosure.md` only when placement is unclear or `SKILL.md` is getting crowded.

## Scripts

Scripts are optional. Add `scripts/` only when code is safer than prose for a repeated deterministic step.

- Good candidates: validation helpers, static checks, file generation, report formatting, deterministic transforms.
- Bad candidates: interactive workflows, host-specific wrappers, web-required setup, or one-off commands.
- A script must stay non-interactive and must not become a hidden prerequisite for ordinary use.
- Document required commands in `SKILL.md` when a script depends on runtime tooling.

Open `references/scripts-guidance.md` only when deciding whether a helper script belongs in the skill or how to document it safely.

## Edge cases

- If scope expands into multiple adjacent jobs, narrow the skill before adding more instructions.
- If a reference is required on most activations, fold its durable guidance back into `SKILL.md`.
- If host or vendor differences share the same job, keep one skill and move deltas to focused references.
- If the workflow depends on web access, rewrite the ordinary path so it remains useful offline and record official-doc verification separately.
- If a version or source baseline matters for normal use, place it in the body near the rules or workflow it constrains.

## Output contract

Return:

1. The skill changes made or proposed.
2. Any changed files or paths.
3. Validation results.
4. Remaining risks, official-documentation blockers, or unresolved scope questions.

## Optional support files

- `assets/skill-template.md` - copy when creating a new skill from scratch.
- `assets/description-patterns.md` - use when rewriting or comparing trigger descriptions.
- `assets/validation-checklist.md` - use for the final validation pass.
- `references/context-budget-and-scope.md` - open when the skill feels too broad, too long, or split at the wrong boundary.
- `references/description-design.md` - open when trigger wording is weak or overly broad.
- `references/progressive-disclosure.md` - open when moving additive material without breaking the ordinary path.
- `references/scripts-guidance.md` - open when adding or reviewing helper scripts.
