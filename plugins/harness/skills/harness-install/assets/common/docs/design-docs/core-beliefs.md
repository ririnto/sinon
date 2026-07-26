# Core Beliefs

## Purpose

This document records the durable agent-first principles behind the repository's design and review rules.
`DESIGN.md` states the rules; these beliefs explain their purpose across changes.

## Beliefs

### Keep agent contracts concise

Use structure and headings to expose the common path.
Keep workflow in `SKILL.md` and exceptions in references/ so long contracts do not hide it.

- Root and child agent contracts stay concise.
  Detailed autonomous procedures live in `SKILL.md`, and deeper context lives in docs/design-docs/ and docs/references/.
- Each reference/ file states its activation condition in the first paragraph (e.g., "Use this when X fails" or "When Y is unclear").
- Skill structure keeps the common path in `SKILL.md` and moves blocker-specific detail to references.
- Agents can reach the routine task without reading more than 3 sections.

### Make agent intent legible

Document intent at the point of use.
Specify output formats, config fields, and design rules so agents can match them without repeated clarification.

- All externally visible declarations carry documentation comments in their native style.
  - Examples: functions, APIs, and config fields.
  - Comment styles include JSDoc, KDoc, TSDoc, and reStructuredText docstrings.
- Execution plans and design decisions use frontmatter and clear task/decision structure.
  - Agents can parse content deterministically without re-reading.
- Repository contracts, validators, and examples name their purpose and constraints upfront.
  - Naming conventions are explicit, not inferred from context.
- Tool output shapes (e.g., validation results, plan formats) are specified with concrete examples and prose descriptions.

### Invariant enforcement scope

Set invariants for minor implementation details and let agents choose the implementation.
Clear boundaries define where agents can act freely.

- Contract rules state *what must not vary* (e.g., validators must not fail silently).
- Implementation order, refactoring patterns, and local style are left to the implementer's judgment.
- Automated checks cover machine-consumed contracts and observable behavior.
- Review covers prose quality, instruction ownership, and local design judgment.
- Design docs distinguish hard constraints from guidelines.
  - Hard constraints cannot change without breaking the contract.
  - Guidelines include recommendations, patterns, and examples.
- Review feedback focuses on invariant violations and new patterns that reveal missing beliefs.
  - It does not focus on local implementation choices that do not affect the contract.

### Version plans, decisions, and quality records

Record decisions in version control rather than chat logs or ephemeral memory.
Version control preserves the reason behind a rule, postmortem history, and auditability across team transitions.

Execution plans live under `docs/exec-plans/` with dates in filenames.
The filename form is `yyyy-MM-dd-<slug>.md`.
Closed plans remain in `docs/exec-plans/` so history and decision rationale stay auditable.

- Design decisions, postmortems, and incident reviews are cited in `core-beliefs.md`.
  - Link to their source so the belief-to-incident chain is traceable.
- `QUALITY_SCORE.md`, `RELIABILITY.md`, and `tech-debt-tracker.md` are version-controlled records.
  - Every consciously deferred item records its retirement criteria.
- Review threads that surface a repeated pattern trigger a durable documentation update.
  - Update `core-beliefs.md`, `DESIGN.md`, or a new `references/` file.

### Maintain documentation during feature work

Update documentation when implementation reality changes.
Regular maintenance keeps contracts aligned and reduces stale guidance and onboarding failures.

- Contract changes are committed as versioned updates during feature work when reality drifts from docs.
  - Examples: updates to agent contracts, `ARCHITECTURE.md`, and validation rules.
  - Commit them during feature work rather than waiting for a separate refactor window.
- Review feedback that reveals a recurring pattern triggers an immediate documentation update.
  - Examples: an applied-but-unwritten design rule or a repeated failure mode.
  - Update `core-beliefs.md`, `DESIGN.md`, or a new `references/` file.
- Validators run locally before commit so agents catch staleness and broken contracts early.
  - Find stale guidance locally before CI runs.
- Maintainers refresh stale examples and quality records during related work.

## When To Update

- When a recurring failure mode contradicts an existing belief, document the incident and update the belief to prevent the pattern.
- When a new belief is needed to explain a recurring review verdict, add the belief rather than repeating the same correction in comments.
- When a belief is retired because the underlying constraint has dissolved, remove it and document why the constraint no longer applies.

## Required Evidence

- Cite the incident, postmortem, PR thread, or design decision that motivated each belief.
- Link to the `DESIGN.md` or `ARCHITECTURE.md` rule that operationalizes each belief so reviewers can trace belief to enforcement.
