# Product Sense

## Purpose

`PRODUCT_SENSE.md` gives agents and humans the product mental model needed to make acceptable trade-offs without escalating: who the user is, what the product values, what tone of voice to use, and what kinds of requests to push back on.

## Product Principles

- Optimize for legibility over cleverness.
- Prefer reversible operations, especially for shared state.
- Default to explicit feedback (error or success) over silent operations.
- Treat plans, decisions, and quality scores as first-class artifacts checked into the repo.
- Make the agent-readable path identical to the human-readable path; if they diverge, fix the agent path first.

## User Model

The primary user is a primary operator who works inside the product five days a week and depends on it for daily workflow completion.
They need stable patterns, bounded delegation, and deterministic validation without constant context-switching.

- Occasional users run specific tasks infrequently and need clear starting points and self-contained instructions.
- Admin operators manage configuration, secrets, and infrastructure surface and require auditable trails and rollback safety.
- External API consumers integrate the product programmatically and need versioned interfaces and explicit replacement timelines.

## Design Tone

- Voice is terse, neutral, and instructive — not casual, not corporate.
- Copy uses sentence case, never title case, except for proper nouns.
- Error messages name the constraint and the next safe action; they never blame the user.
- Empty states explain what would appear here and how to populate it.
- Use plain language; avoid jargon unless it is the user's working vocabulary.

## Refusal Signals

- Refuse requests that bypass review, such as force-merge or `--no-verify`.
- Refuse silent destructive operations (drop, truncate, rm -rf) without explicit confirmation.
- Refuse scope expansions beyond the active milestone in `PLANS.md`; redirect to the next milestone.
- Refuse to invent values for required identifiers (IDs, URLs, secrets); ask or fail.
- Refuse to implement features that lack a matching product spec in docs/product-specs/.

## When To Update

- When a product principle is added or retired.
- When the primary user persona shifts or secondary needs change significantly.
- When the team agrees a previously accepted request pattern should now be refused.

## Required Evidence

- Cite the product spec under `docs/product-specs/` or the decision record under `docs/design-docs/` that introduced each principle.
- Link to the PR or issue where a refusal pattern was formalized.
