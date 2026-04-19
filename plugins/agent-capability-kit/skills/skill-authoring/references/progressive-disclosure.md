# Progressive Disclosure

Open this file when `SKILL.md` is getting crowded and you need to decide whether a section belongs in `SKILL.md`, `references/`, `assets/`, or `scripts/`.

Put the shortest stable guidance in `SKILL.md`, and move depth into support files.

## What belongs in `SKILL.md`

Keep only what the model must know immediately after the skill activates:

- purpose
- hard rules
- exact step order
- edge-case handling
- output contract
- short progress checklist, if it prevents missed phases
- short gotchas, if they prevent common mistakes
- direct references to supporting files

## What belongs in `references/`

Use for material that helps execution but does not need to be injected every time:

- deeper explanations
- specification summaries
- checklists that are too detailed for the main file
- compatibility notes
- troubleshooting guidance
- scope-splitting guidance
- scripts decision guidance

Each file should say when to open it, for example when a host-specific branch is needed or when the main workflow hits a concrete blocker.

## What belongs in `assets/`

Use for copyable deliverables:

- JSON schemas
- OpenAPI fragments
- Markdown templates
- starter config files
- sample request bodies
- example prompt blocks
- validation checklists

## What belongs in `scripts/`

Use for repeatable execution steps that are better expressed as code:

- validation
- extraction
- transformation
- packaging
- static checks
- report generation

Do not use `scripts/` as a place to hide required ordinary-path logic.

## Context-budget test

When deciding what to move out of `SKILL.md`, ask:

1. Is this needed on most activations?
2. Is this part of the first safe path?
3. Is this reusable as a copyable asset instead?
4. Is this really a blocker-specific branch?

If the answer to all four is no, move it out of the main file.

## Good split

```text
SKILL.md                           -> 140 lines of action logic and defaults
references/context-budget.md       -> scope repair guidance
assets/template.json               -> starter schema
scripts/validate.sh                -> deterministic checks
```

## Bad split

```text
SKILL.md -> 700 lines of mixed instructions, tutorials, examples, and raw templates
```

## Authoring rule

If a section is mostly reference material and not immediate action logic, move it out of `SKILL.md`.

If the section is needed for the ordinary path every time the skill activates, keep it in `SKILL.md` instead.
