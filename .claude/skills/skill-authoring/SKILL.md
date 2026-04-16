---
name: skill-authoring
description: >-
  Use this skill when the user asks to write a new skill, review or refactor an existing skill, tighten a skill description, decide what belongs in SKILL.md versus references, or raise a skill to the repository's current quality bar for consistency, structure, and clear scope boundaries.
---

# Skill Authoring

## Overview

Use this skill to author repository-local skills that follow the agentskills.io loading hierarchy, stay self-sufficient in the common case, and route by user intent. The standard shape is one coherent skill directory, one `SKILL.md` for the common path, and optional `references/` files for blocker-driven depth.

Treat agentskills.io as the structural basis, then normalize to this repository's local rules: metadata routes activation, `SKILL.md` carries the common path, and `references/` hold focused on-demand depth.

## Use This Skill When

- You are creating a new skill under `.claude/skills/`, `.opencode/skills/`, or another repository-local skill surface.
- You are reviewing whether a skill is consistent, structured, and explicit about its role.
- You are tightening a `description` so it routes by user intent instead of internal implementation wording.
- You are deciding what belongs in `SKILL.md` and what should move into `references/`.
- You are adding templates, commands, checklists, or validation guidance to make a skill usable without extra interpretation.
- You are reconciling repository-specific expectations with agentskills.io-first skill conventions and any secondary runtime-specific compatibility constraints.
- Do not use this skill when the main task is the domain content of another skill rather than the structure and authoring quality of the skill itself.

## Common-Case Workflow

1. Read the repository rules and 2-3 nearby skill examples.
2. Define one coherent user job. If sibling skills would share the same common path and differ mainly by host, vendor, or platform deltas, merge them first.
3. Write the `description` as a routing key in user-intent language.
4. Make `SKILL.md` sufficient for the common path.
5. Add `references/` only for blocker-based depth such as routing calibration, host/vendor/platform deltas, or edge-case structure rules.
6. Validate self-sufficiency, progressive disclosure, coherent-unit sizing, and routing clarity.

## Skill Design Decision Path

- Start with one question: what repeatable job does this skill help an agent finish correctly?
- If the common case can be completed from one file, keep it in `SKILL.md`.
- If sibling skills share the same common path and differ mainly by host, vendor, or platform details, merge them and move only the deltas into focused references.
- If a deeper topic is only needed after a specific blocker appears, move it to `references/`.
- If the skill needs executable helpers or generated artifacts, add `scripts/` or `assets/` only when the workflow actually depends on them.
- If two references are almost always read together for one blocker, merge them.
- If a reference is likely to be read in the common path, fold its durable guidance back into `SKILL.md`.
- If the `description` only names technologies or internals, rewrite it in task language that a user would naturally say.

## Minimal Setup

Start from the smallest valid tree:

```text
.claude/
  skills/
    skill-name/
      SKILL.md
```

Add additive depth only when needed:

```text
.claude/
  skills/
    skill-name/
      SKILL.md
      references/
        blocker-one.md
        blocker-two.md
```

Merged multi-platform shape when the common path is shared:

```text
.claude/
  skills/
    skill-name/
      SKILL.md
      references/
        github-deltas.md
        gitlab-deltas.md
```

Setup rules:

- the directory name MUST match the `name` in `SKILL.md`
- `name` SHOULD stay lowercase kebab-case
- `description` MUST tell the agent what the skill helps with and when it should activate
- `SKILL.md` SHOULD stay compact enough to scan quickly during activation
- `references/` MUST remain additive, not required for the ordinary path
- one merged skill MAY span multiple hosts or platforms when the common path remains in `SKILL.md`

## Ready-to-Adapt Templates

Minimum scaffold:

`````markdown
---
name: skill-name
description: >-
  Use this skill when the user asks to [job], needs [outcome], or wants help with [task-shaped trigger].
---

# Skill Title

## Overview

Use this skill to [primary purpose]. The common case is [ordinary path].

## Use This Skill When

- You are [trigger].
- You are [trigger].
- Do not use this skill when [adjacent-domain exclusion].

## Common-Case Workflow

1. Read [inputs].
2. Decide [main branch].
3. Apply [default path].
4. Validate [result].

## Ready-to-Adapt Templates

```text
[Put the highest-value reusable output shape here.]
```

## Validate the Result

- [check]
- [check]

## Scope Boundaries

- Activate this skill for:
  - [job]
- Do not use this skill as the primary source for:
  - [adjacent job]
`````

Description pattern:

```yaml
description: >-
  Use this skill when the user asks to [task in user language], needs [outcome], or wants help with [specific repeatable job].
```

Deep reference routing table:

```markdown
## Deep References

| If the blocker is... | Read... |
| --- | --- |
| tightening trigger phrases or should-trigger vs should-not-trigger calibration | `./references/description-routing.md` |
| deciding whether content belongs in `SKILL.md` or `references/` | `./references/progressive-disclosure.md` |
```

Merged multi-platform baseline:

```markdown
## Overview

Use this skill to [shared job]. The common case is [shared path]. Branch into host-specific references only when exact platform mechanics are the blocker.

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| exact GitHub-specific deltas | `./references/github-deltas.md` |
| exact GitLab-specific deltas | `./references/gitlab-deltas.md` |
```

Reference opener pattern:

```markdown
---
title: Reference Title
description: >-
  Reference for [one concrete blocker or job].
---

Use this reference when the blocker is [specific blocker] rather than [what stays in SKILL.md]. This file should be sufficient on its own to finish that job.
```

## Validate the Result

- the directory name and `SKILL.md` `name` match exactly
- the `description` reads like a routing key driven by user intent
- `SKILL.md` alone can guide the ordinary authoring or review task without opening references
- the common path contains concrete templates, commands, or output shapes instead of prose-only advice
- host/vendor/platform differences are pushed into references only when the shared workflow already lives in `SKILL.md`
- each reference file starts with a blocker-based condition for opening it
- references add depth without duplicating the canonical templates, workflow steps, or invariants owned by `SKILL.md`
- invariants use BCP 14 language when they express stable rules
- scope boundaries exclude adjacent domains in domain terms rather than by cross-skill handoff
- the skill reads as one coherent unit of work instead of a loose bundle of unrelated advice

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| tightening trigger wording, should-trigger examples, or description calibration | `./references/description-routing.md` |
| deciding what belongs in `SKILL.md` versus `references/` or other support files | `./references/progressive-disclosure.md` |
| reconciling agentskills.io-first rules with secondary runtime or vendor-specific compatibility details | `./references/source-synthesis.md` |

## Invariants

- `SKILL.md` MUST be the canonical entrypoint for the skill.
- The `name` in `SKILL.md` MUST exactly match the skill directory basename.
- The `description` MUST route by user intent and SHOULD use “Use this skill when...” framing.
- `SKILL.md` MUST remain self-sufficient for the common case.
- Common-case workflow steps, canonical templates, and primary validation checks MUST stay in `SKILL.md`.
- `references/` MUST contain additive depth only and MUST open with a concrete blocker-based condition.
- A skill SHOULD cover one coherent unit of work and route precisely.
- Sibling skills that share the same common path and differ mainly by host, vendor, or platform deltas SHOULD be merged.
- A skill MUST remain usable when installed by itself and MUST NOT require another skill for basic execution.
- A skill MUST add project-specific or non-obvious value instead of restating generic model knowledge.
- Code-heavy or format-critical skills SHOULD teach through copy-adaptable examples rather than essay-style explanation.
- Scope boundaries MUST describe adjacent-domain exclusions in domain terms.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| writing a broad description that only names technologies or file types | activation becomes noisy and imprecise | describe the user task, expected outcome, and natural trigger phrases |
| putting the common path into `references/` to keep `SKILL.md` short | the skill stops being usable by itself | keep the normal workflow and core templates in `SKILL.md` |
| splitting one ordinary task into sibling host-specific skills with the same common path | routing overhead rises and the common path gets duplicated | merge the shared path into one skill and move only host-specific deltas into references |
| mixing multiple unrelated jobs into one skill | routing precision and scanability collapse | split the work into coherent units or narrow the promised surface |
| treating secondary runtime or vendor docs as co-equal with the agentskills.io-based repository hierarchy | the local skill loses a stable structural basis | use agentskills.io as the structural basis and treat runtime/vendor docs as compatibility deltas |

## Scope Boundaries

- Activate this skill for:
  - writing new repository-local skills
  - reviewing or refactoring existing skill structure
  - calibrating skill descriptions and routing language
  - deciding common-path versus deep-reference placement
  - raising a skill to the repository's current quality bar
- Do not use this skill as the primary source for:
  - the domain-specific content of another skill
  - generic prompting advice with no skill packaging context
  - slash-command authoring or other agent surfaces that do not use `SKILL.md` as the canonical entrypoint
