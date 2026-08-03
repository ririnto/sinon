# Agent Skills Format

## Directory

Each skill is one directory containing a file named exactly `SKILL.md`.

Optional `scripts`, `references`, and `assets` directories belong inside that skill directory.

The skill directory name and frontmatter `name` must match.

## Frontmatter

`name` is required, uses 1 to 64 lowercase alphanumeric or hyphen characters, cannot start or end with a hyphen, and cannot contain consecutive hyphens.

`description` is required, uses 1 to 1024 characters, and states both what the skill does and when to use it with discriminative keywords.

Add optional fields only when a consumer uses them.

Keep metadata values stable and omit timestamps or generated fields that do not affect loading.

## Progressive Disclosure

The session catalog loads only skill names and descriptions.

The full `SKILL.md` body loads when the skill is activated.

Supporting resources load only when the activated instructions name a specific need for them.

Keep `SKILL.md` below 500 lines and approximately 5,000 tokens.

Move detail into small focused resource files when the core instructions exceed that budget.

## Composition

Give each skill one coherent purpose that can complete its normal workflow without loading sibling skills.

State every resource trigger in the owning `SKILL.md`.

Keep file references one level deep from `SKILL.md`.

Make each reference self-contained and prohibit reference-to-reference chains.

Expose no disabled or inaccessible skill in the catalog.

## Scripts and Assets

Bundle a script when repeated execution traces show the agent recreating the same deterministic logic.

Document script prerequisites and make errors actionable.

Store long templates and static resources as assets and load them only for the output that needs them.
