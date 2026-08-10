# Payload Contracts

## Context Partition

Separate decision-bearing steering from node-local working material.

Steering can include the outcome, scope, dependencies, decisions, constraints, resource ownership, authority, acceptance criteria, required evidence, stop condition, interfaces, and exact excerpts needed for a decision.

Working material includes source bodies, patches, logs, screenshots, traces, transcripts, and exploratory notes.
Keep working material node-local.

## Dispatch Payload

Give each node one self-contained payload.
State the independently testable outcome, owned scope, allowed actions and side effects, acceptance criteria, required evidence, and stop condition.

Add predecessor dependencies, decision-bearing predecessor state, task-specific constraints, owned mutable resources, integration interfaces, exact excerpts, and non-goals only when they apply.
Do not add empty sections or unstated session context.

## Edge State

A node receives only the steering needed from its declared predecessors.
An independent node receives no predecessor result.
Compose needed predecessor state into one payload.
Do not append or replay result history.
Carry every discovered breaking change cumulatively through terminal results, dependent payloads, and final synthesis.
Preserve decision-bearing state exactly when a downstream decision depends on it.
Send a minimal exact excerpt only when paraphrase would lose that state.

## Terminal Result

Return one terminal result through the result channel.
A terminal node outcome is completed, blocked, failed, or unknown.

Use completed only when all acceptance criteria pass.
Use blocked when further in-scope work needs missing information or authority.
Use failed when required work or validation fails.
Use unknown when available evidence cannot prove another outcome.

Give the achieved outcome and required evidence when available.
Identify changed resources, downstream decisions, released resources, and blockers only when they apply.
Do not include raw working material.
Preserve exact decision-bearing values that downstream integration needs.

## Task-Specific Interfaces

Define an exact payload or result shape only when its field structure materially improves dispatch, acceptance, deterministic synthesis, or recovery.
Otherwise, state the information requirements in prose.
