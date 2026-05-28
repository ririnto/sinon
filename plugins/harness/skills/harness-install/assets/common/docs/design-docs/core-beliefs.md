# Core Beliefs

## Purpose

Core beliefs capture the durable agent-first operating principles that govern every design and review decision in this repository. Where DESIGN.md articulates *what* the rules are, this document articulates *why* the rules exist — the underlying convictions that make the rules worth preserving across changes.

## Beliefs

### Progressive disclosure beats one-shot context

Agents pattern-match on structure and heading presence; when a single CLAUDE.md or AGENTS.md grows above ~150 lines, agents waste cycles scanning dense sections and miss the common path. Progressive layering (SKILL.md for workflow, references/ for exceptions) keeps the activation surface lean and direct.

- CLAUDE.md, AGENTS.md, and SKILL.md files stay under ~150 lines; deeper context lives in docs/design-docs/ and docs/references/.
- Each reference/ file states its activation condition in the first paragraph (e.g., "Use this when X fails" or "When Y is unclear").
- Skill structure separates common-case examples from edge-case templates; validators reject references that duplicate common-path content.
- Agents can reach the routine task without reading more than 3 sections.

### Agent legibility is a first-class quality attribute

Agents cannot backtrack through unclear intent or re-read undocumented decisions. When a tool output format is undocumented, when a config field's purpose lives only in a comment, or when a design rule is implicit, agents either pattern-match incorrectly or request clarification repeatedly, burning context.

- All externally visible declarations (functions, APIs, config fields) carry documentation comments in their native style (JSDoc, KDoc, TSDoc, reStructuredText docstrings).
- Execution plans and design decisions use frontmatter and clear task/decision structure so agents parse content deterministically without re-reading.
- Harness manifests, validators, and template examples name their purpose and constraints upfront; naming conventions are explicit, not inferred from context.
- Tool output shapes (e.g., validation results, plan formats) are specified with concrete examples, not just prose descriptions.

### Enforce invariants, do not micromanage implementations

Heavy-handed style reviews and approval gates on minor details create friction in agent workflows and slow down routine changes. When every commit needs sign-off on formatting or tooling choices, agents cannot iterate; when rules are not clear about what is actually non-negotiable, agents apply caution where they should act freely.

- Harness rules state *what must not vary* (e.g., CLAUDE.md and AGENTS.md resolve to the same root contract, validators must not fail silently); implementation order, refactoring patterns, and local style are left to the implementer's judgment.
- Validators check invariants only (boundary enforcement, manifest schema, required directories), not code style, comment density, or variable names.
- Design docs distinguish hard constraints (cannot be changed without breaking the contract) from guidelines (recommendations, patterns, examples); agents and reviewers treat them accordingly.
- Review feedback focuses on invariant violations and new patterns that reveal missing beliefs, not on local implementation choices that do not affect the contract.

### Plans, decisions, and quality scores are versioned artifacts

Chat logs and ephemeral memory vanish after context resets or when teams grow. If decisions live only in threads or agent memory, future implementers cannot find the *why* behind a rule, postmortems are lost, and the same debates recur. Version control is the only source of truth that survives a team transition.

- Active execution plans live under docs/exec-plans/active/ with dates in filenames (`yyyy-MM-dd-<slug>.md`); completed plans move to completed/ without renaming so history and decision rationale remain auditable.
- Design decisions, postmortems, and incident reviews are cited in core-beliefs.md with links to their source so the belief-to-incident chain is traceable.
- QUALITY_SCORE.md, RELIABILITY.md, and tech-debt-tracker.md are version-controlled records; every consciously deferred item records its retirement criteria so the project knows what unblocks it.
- Review threads that surface a repeated pattern trigger an update to core-beliefs.md, DESIGN.md, or a new reference/ file so the verdict persists beyond the PR.

### Continuous gardening over occasional cleanups

Deferred documentation and technical debt compound silently; by the time a refactor sprint happens, agents have already wasted cycles on stale guidance, and the project is harder to navigate. Small, incremental care keeps the harness and docs aligned with reality and reduces surprise failures during onboarding.

- Harness changes (updates to CLAUDE.md, ARCHITECTURE.md, validation rules) are committed as versioned updates during feature work when reality drifts from docs; they do not wait for a separate refactor window.
- Review feedback that reveals a recurring pattern (a design rule that is applied but not written, a failure mode that shows up twice) triggers an immediate update to core-beliefs.md, DESIGN.md, or a new reference/ file.
- Validators run locally before commit (pre-commit hook) so agents catch staleness and broken contracts early; CI does not become the discovery mechanism for document rot.
- A weekly or biweekly doc-gardening agent opens PRs to update stale examples, refresh QUALITY_SCORE.md, and surface tech-debt-tracker.md items that are ready for resolution.

## When To Update

- When a recurring failure mode contradicts an existing belief, document the incident and update the belief to prevent the pattern.
- When a new belief is needed to explain a recurring review verdict, add the belief rather than repeating the same correction in comments.
- When a belief is retired because the underlying constraint has dissolved, remove it and document why the constraint no longer applies.

## Required Evidence

- Cite the incident, postmortem, PR thread, or design decision that motivated each belief.
- Link to the DESIGN.md or ARCHITECTURE.md rule that operationalizes each belief so reviewers can trace belief to enforcement.
