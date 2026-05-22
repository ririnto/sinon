# Product Sense

## Purpose

PRODUCT_SENSE.md gives agents and humans the product mental model needed to make acceptable trade-offs without escalating: who the user is, what the product values, what tone of voice to use, and what kinds of requests to push back on.

## Product Principles

- Optimize for legibility over cleverness.
- Prefer reversible operations.
- Default to explicit feedback over silent success.

## User Model

The primary user is a developer integrating coding agents into a project harness who needs stable patterns, bounded delegation, and deterministic validation without constant context-switching.

- Secondary users include reviewers enforcing product standards and new teammates learning the harness structure from examples.
- Contributors building plugins and skills need templates that evolve with the product but remain self-contained.

## Design Tone

- Write in imperative, direct language; skip preamble and filler.
- Use kebab-case for configuration keys and reserved identifiers.
- Keep error messages actionable: name the problem, state what to check, link to relevant docs.
- Empty-state copy SHOULD explain what belongs in this section and when to populate it.

## Refusal Signals

- Requests to remove validation gates or bypass review; respond with the design principle and validation command.
- Scope expansions beyond the active milestone without a design review; respond with a pointer to the active plan and link to the design-docs directory.
- Prompts that imply destructive shortcuts (deleting files, overwriting templates, skipping harness evolution); respond with the reversibility principle and an alternative approach.

## When To Update

- When a product principle is added or retired.
- When the primary user persona shifts or secondary needs change significantly.
- When the team agrees a previously accepted request pattern should now be refused.

## Required Evidence

- Cite the product spec under `docs/product-specs/` or the decision record under `docs/design-docs/` that introduced each principle.
- Link to the PR or issue where a refusal pattern was formalized.
