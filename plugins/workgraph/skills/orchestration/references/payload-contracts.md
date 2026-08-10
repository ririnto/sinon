# Payload Contracts

## Context Partition

Separate decision-bearing steering from node-local working material.

Steering can include the outcome, scope, dependencies, decisions, constraints, resource ownership, authority, acceptance criteria, required evidence, stop condition, interfaces, and exact excerpts needed for a decision.

Working material includes source bodies, patches, logs, screenshots, traces, transcripts, and exploratory notes.
Keep working material node-local.

## Dispatch Payload

Give each node one self-contained payload.
Make it describe the node's stable identity and local Graph slice in prose. Explain its place in the topology, selected model alias, authority and owner, mutable-resource scope, predecessor dependencies, independently testable outcome, acceptance evidence, and terminal condition. Include only topology and edge state relevant to the node's decisions; omit unrelated branches and raw history.
State the allowed actions and side effects, integration interfaces, and stop condition.

Separate scheduling dependencies from information visibility.
Declare a dependency when a predecessor must finish before dispatch or integration. A resource conflict is a scheduling dependency even when no predecessor state is passed.
Pass predecessor state only when it changes the dependent decision.
Add task-specific constraints, exact excerpts, and non-goals only when they apply.
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

Curate discovery findings into material claims with exact evidence locators.
For local evidence, include the current path, symbol or line, and relevant artifact or revision state.
For external evidence, include the URL and release, version, revision, or checked-at date.
State uncertainty or conflict, downstream implication, and the next authoritative read when useful.
Treat absence claims as unknown unless search coverage is sufficient and stated.

Give the achieved outcome and required evidence when available.
Identify changed resources, downstream decisions, released resources, and blockers only when they apply.
Do not include raw working material.
Preserve exact decision-bearing values that downstream integration needs.

## Side Effects and Loops

Carry applicable side-effect class and rerun steering only when they change a dispatch or recovery decision.
Do not claim automatic recovery.

Every semantic loop needs a stated exit condition.
Do not silently truncate dynamic expansion or join before all required terminal branch outcomes arrive.

## Task-Specific Interfaces

Define an exact payload or result shape only when a real machine consumer needs deterministic fan-in, automated recovery, or field-validation interoperability.
Dispatch convenience, acceptance wording, and ordinary prose synthesis alone do not justify an exact shape.
Otherwise, state the information requirements in prose.
