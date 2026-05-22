# Core Beliefs

## Purpose

Core beliefs capture the durable agent-first operating principles that govern every design and review decision in this repository. Where DESIGN.md articulates *what* the rules are, this document articulates *why* the rules exist — the underlying convictions that make the rules worth preserving across changes.

## Beliefs

### Progressive disclosure beats one-shot context

Agents reason better with information presented in layers, starting with common-case workflow and decision points, then expanding to references only when needed. Dumping all context at once makes discovery harder and slows down routine work.

- Skill SKILL.md files hold common-case procedures and examples; references/ holds edge cases and operational cautions.
- Larger docs are organized with headings and lists, not dense prose, so agents can scan and navigate.
- Each reference file states its blocker condition upfront so agents know when to open it.

### Agent legibility is a first-class quality attribute

Code, configs, and documentation must be readable by the agents that interact with them, not just humans. When a tool output is opaque, when a config structure is buried in comments, or when a design decision is undocumented, agents waste work re-discovering it or working around it.

- All externally visible declarations carry documentation comments in their native style (JSDoc, KDoc, reStructuredText).
- Execution plans use markdown with clear task structure so agents can parse and manage them.
- Harness manifests and templates declare their purpose upfront; magic or implicit conventions are avoided.

### Enforce invariants, do not micromanage implementations

Focus review on things that cannot be loosened without breaking the contract (public API shapes, validation gates, deployment order). Release everything else to the implementer's judgment.

- Harness rules state *what must not vary* (e.g., CLAUDE.md is primary, AGENTS.md is a symlink); implementation order and refactoring details are left open.
- Validators check invariants, not style; style is enforced through examples and coding conventions, not blockers.
- Design docs set boundaries (what can be versioned, what cannot); within those bounds, agents and humans choose freely.

### Plans, decisions, and quality scores are versioned artifacts

Ephemeral chat and memory do not survive context resets or team onboarding. Decisions that steer the project must be written down, time-stamped, and kept in the repository so they remain discoverable and auditable.

- Active execution plans live under docs/exec-plans/active/ with dates in filenames; completed plans move to completed/ so history remains intact.
- Design decisions and postmortems are cited in core-beliefs.md as evidence, not buried in Slack threads.
- QUALITY_SCORE.md and RELIABILITY.md exist as version-controlled records, not comments in chat.

### Continuous gardening over occasional cleanups

Technical debt and documentation debt grow if they are not tended regularly. Waiting for a refactor window wastes time; instead, reserve a small recurring budget for fixes, clarifications, and refactoring.

- Harness changes can be made during feature work when reality drifts from docs; such changes are committed as versioned updates.
- Review feedback that reveals a pattern (a recurring failure mode, an unclear guideline) triggers a belief or rule update immediately, not a ticket.
- Skills, agents, and docs are kept current alongside the code they guide; staleness is a bug to fix, not a backlog item.

## When To Update

- When a recurring failure mode contradicts an existing belief, document the incident and update the belief to prevent the pattern.
- When a new belief is needed to explain a recurring review verdict, add the belief rather than repeating the same correction in comments.
- When a belief is retired because the underlying constraint has dissolved, remove it and document why the constraint no longer applies.

## Required Evidence

- Cite the incident, postmortem, PR thread, or design decision that motivated each belief.
- Link to the DESIGN.md or ARCHITECTURE.md rule that operationalizes each belief so reviewers can trace belief to enforcement.
