# Core Beliefs

## Purpose

Core beliefs capture the durable agent-first operating principles that govern every design and review decision in this repository.
Where `DESIGN.md` articulates *what* the rules are, this document articulates *why* the rules exist.
These are the convictions that make the rules worth preserving across changes.

## Beliefs

### Progressive disclosure beats one-shot context

Agents pattern-match on structure and heading presence.
When `AGENTS.md` grows above ~150 lines, agents waste cycles scanning dense sections and miss the common path.
Progressive layering (`SKILL.md` for workflow, references/ for exceptions) keeps the activation surface lean and direct.

- `AGENTS.md` and `SKILL.md` files stay under ~150 lines; deeper context lives in docs/design-docs/ and docs/references/.
- Each reference/ file states its activation condition in the first paragraph (e.g., "Use this when X fails" or "When Y is unclear").
- Skill structure separates common-case examples from edge-case templates; validators reject references that duplicate common-path content.
- Agents can reach the routine task without reading more than 3 sections.

### Agent legibility is a first-class quality attribute

Agents cannot backtrack through unclear intent or re-read undocumented decisions.
When a tool output format is undocumented, when a config field's purpose lives only in a comment, or when a design rule is implicit, agents either pattern-match incorrectly or request clarification repeatedly, burning context.

- All externally visible declarations carry documentation comments in their native style.
  - Examples: functions, APIs, and config fields.
  - Comment styles include JSDoc, KDoc, TSDoc, and reStructuredText docstrings.
- Execution plans and design decisions use frontmatter and clear task/decision structure.
  - Agents can parse content deterministically without re-reading.
- Repository contracts, validators, and examples name their purpose and constraints upfront.
  - Naming conventions are explicit, not inferred from context.
- Tool output shapes (e.g., validation results, plan formats) are specified with concrete examples and prose descriptions.

### Enforce invariants, do not micromanage implementations

Heavy-handed style reviews and approval gates on minor details create friction in agent workflows and slow down routine changes.
When every commit needs sign-off on formatting or tooling choices, agents cannot iterate; unclear rules make agents cautious where they should act freely.

- Contract rules state *what must not vary* (e.g., validators must not fail silently).
- Implementation order, refactoring patterns, and local style are left to the implementer's judgment.
- Validators check invariants only, not code style, comment density, or variable names.
  - Examples: boundary enforcement, required files and directories, and hook executability.
- Design docs distinguish hard constraints from guidelines.
  - Hard constraints cannot change without breaking the contract.
  - Guidelines include recommendations, patterns, and examples.
- Review feedback focuses on invariant violations and new patterns that reveal missing beliefs.
  - It does not focus on local implementation choices that do not affect the contract.

### Plans, decisions, and quality scores are versioned artifacts

Chat logs and ephemeral memory vanish after context resets or when teams grow.
If decisions live only in threads or agent memory, future implementers cannot find the *why* behind a rule, postmortems are lost, and the same debates recur.
Version control is the only source of truth that survives a team transition.

Execution plans live under `docs/exec-plans/` with dates in filenames.
The filename form is `yyyy-MM-dd-<slug>.md`.
Closed plans remain in `docs/exec-plans/` so history and decision rationale stay auditable.

- Design decisions, postmortems, and incident reviews are cited in `core-beliefs.md`.
  - Link to their source so the belief-to-incident chain is traceable.
- `QUALITY_SCORE.md`, `RELIABILITY.md`, and `tech-debt-tracker.md` are version-controlled records.
  - Every consciously deferred item records its retirement criteria.
- Review threads that surface a repeated pattern trigger a durable documentation update.
  - Update `core-beliefs.md`, `DESIGN.md`, or a new `references/` file.

### Continuous gardening over occasional cleanups

Deferred documentation and technical debt compound silently.
By the time a refactor sprint happens, agents have already wasted cycles on stale guidance and the project is harder to navigate.
Small, incremental care keeps the contracts and docs aligned with reality and reduces surprise failures during onboarding.

- Contract changes are committed as versioned updates during feature work when reality drifts from docs.
  - Examples: updates to `AGENTS.md`, `ARCHITECTURE.md`, and validation rules.
  - They do not wait for a separate refactor window.
- Review feedback that reveals a recurring pattern triggers an immediate documentation update.
  - Examples: an applied-but-unwritten design rule or a repeated failure mode.
  - Update `core-beliefs.md`, `DESIGN.md`, or a new `references/` file.
- Validators run locally before commit so agents catch staleness and broken contracts early.
  - CI does not become the discovery mechanism for document rot.
- A weekly or biweekly doc-gardening agent opens PRs for stale examples and quality records.
  - Refresh `QUALITY_SCORE.md`.
  - Surface `tech-debt-tracker.md` items that are ready for resolution.

## When To Update

- When a recurring failure mode contradicts an existing belief, document the incident and update the belief to prevent the pattern.
- When a new belief is needed to explain a recurring review verdict, add the belief rather than repeating the same correction in comments.
- When a belief is retired because the underlying constraint has dissolved, remove it and document why the constraint no longer applies.

## Required Evidence

- Cite the incident, postmortem, PR thread, or design decision that motivated each belief.
- Link to the `DESIGN.md` or `ARCHITECTURE.md` rule that operationalizes each belief so reviewers can trace belief to enforcement.
