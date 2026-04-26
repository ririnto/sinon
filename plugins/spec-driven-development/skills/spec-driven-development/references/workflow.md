---
title: Workflow
description: >-
  Full specification-driven development workflow with conditional research,
  approval gates, review evidence requirements, and implementation sync rules.
---

## Overview

Open this guide only when you need the full lifecycle semantics beyond the ordinary path in `../SKILL.md`.
This workflow is iterative.
Any review MAY return the work to an earlier stage.
`SPEC.md` remains the source of truth for scope and intended behavior.

## Table of Contents

- [Fast Path](#fast-path)
- [Status Lifecycle](#status-lifecycle)
- [Gates](#gates)
  - [Gate 1 — SPEC Setup Complete](#gate-1--spec-setup-complete)
  - [Gate 2 — Spec Review Passed](#gate-2--spec-review-passed)
- [Research](#research)
- [SPEC Setup](#spec-setup)
- [Document Linking](#document-linking)
- [Optional Interface Contract Authoring](#optional-interface-contract-authoring)
- [Spec Review](#spec-review)
- [Implementation](#implementation)
- [Implementation Review](#implementation-review)
- [Review Evidence Contract](#review-evidence-contract)
- [Subagent Invocation Pattern](#subagent-invocation-pattern)

## Fast Path

`../SKILL.md` owns the ordinary offline-capable path.
This section keeps only a compact lifecycle summary.

1. Use Research only when external framework, library, or topic behavior is unclear.
2. Create or revise `SPEC.md`, obtain Gate 1 approval, then link outbound `call` dependencies.
3. Add optional contract artifacts only when they improve review clarity.
4. Run Spec Review with the inline checklist in `../SKILL.md` and `"${SKILL_ROOT}/scripts/sdd.sh" validate ./spec` when `uv` can run from locally available inputs.
5. Start implementation only after the approved artifact set is explicit.
6. Finish with Implementation Review, final spec sync, and re-run `"${SKILL_ROOT}/scripts/sdd.sh" validate ./spec` when `uv` can run from locally available inputs; otherwise document the runtime blocker and complete the manual checklist fallback.

## Status Lifecycle

- `draft`: initial authoring has started.
- `review`: the artifact is ready for formal review.
- `approved`: scope is accepted and implementation may start.
- `wip`: implementation is in progress.
- `implemented`: implementation review and verification are complete.
- `deprecated`, `superseded`, `removed`: retirement or replacement states.

## Gates

Gates are explicit checkpoints that block forward progress until their conditions are met.

### Gate 1 — SPEC Setup Complete

Passes when the user has explicitly approved the scope, primary requirements, and scenario direction of the current `SPEC.md` draft.

The agent MUST present a scope summary and request explicit approval before advancing to Document Linking.

### Gate 2 — Spec Review Passed

Passes when both of the following conditions are met:

- Every applicable inline checklist item in `../SKILL.md` is recorded as `pass` or `n/a` (zero `fail` items remain).
- `"${SKILL_ROOT}/scripts/sdd.sh" validate ./spec` exits with status 0 when `uv` can resolve its Python runtime plus required dependency/build artifacts from local cache or local files. When `uv` is unavailable or cannot run from locally available inputs, the review record documents the runtime blocker and every applicable inline-checklist item is completed manually.

## Research

Entry:
External behavior, library behavior, or version-sensitive constraints are unclear.

Activities:

1. `RESEARCH.md` MUST be created under `spec/research/{framework|library|topic}/{name}/`. Authors MAY start by copying `assets/templates/RESEARCH.md` into that canonical path.
2. The investigated subject MUST be an external framework, library, or topic that can affect later spec decisions.
3. The investigation question, scope boundary, and explicit non-goals SHOULD be written first.
4. Confirmed facts MUST be separated from hypotheses and unknowns.
5. `subject.name` and `subject.version` MUST be filled.
6. `subject.url` MAY be added when a stable reference is useful.
7. Research MUST remain evidence-oriented. It MUST NOT prescribe rollout plans or implementation commitments. It MUST NOT be used for project comparison, repository audits, implementation planning, migration sequencing, or task management.
8. Research SHOULD be refreshed before Spec Review when findings materially affect requirements or constraints.

Exit:

- External behavior is clear enough to author or revise `SPEC.md`.
- If research affects requirements, the latest findings are reflected before Spec Review.

## SPEC Setup

Entry:
The user requests a new feature or a capability change.
If Research was needed, the active investigation findings already exist.

Activities:

1. The spec path MUST be determined from ownership rules that map to the owning capability boundary.
2. `spec/domain/<ownership-path>/SPEC.md` MUST be created. The `<ownership-path>` MUST reflect the owning capability boundary. Authors MAY copy `assets/templates/SPEC.md` as a starting scaffold.
3. `spec/CHANGELOG.md` MUST exist. `CHANGELOG.md` MUST NOT be created inside individual spec directories. Only `spec/CHANGELOG.md` is permitted.
4. Necessity, Role, and Overview SHOULD be completed first.
5. Scope ownership MUST be confirmed: the SPEC MUST own a single capability and MUST NOT duplicate another SPEC's requirements.
6. Functional Requirements with verifiable outcomes MUST be defined. Requirements MUST follow the authoring flow `spec -> code`. Requirements MUST NOT be reverse-derived from current implementation.
7. Scenarios MUST include `Normal Flow`, `Alternative Flow`, and `Error Flow`.
8. Scenarios SHOULD make requirement coverage explicit.
9. Key Entities and Constraints SHOULD be completed before approval.
10. The SPEC SHOULD reference current research findings when external behavior constrains the design.
11. The SPEC SHOULD remain implementation-agnostic by default. It SHOULD avoid introducing language, framework, library, or code-style constraints unless the user explicitly requests them or verified external constraints make them necessary.

Exit:

- Gate 1 applies here.
- The user MUST approve scope, primary requirements, and scenario direction before the workflow continues.
- If an agent authored the draft, the user MUST review it before the next stage.

## Document Linking

Entry:
Gate 1 passed and `SPEC.md` scope is approved.

Activities:

1. Outbound dependencies MUST be identified from the calling SPEC.
2. Frontmatter `call` entries MUST be added for outbound targets only.
3. Each `call` entry MUST resolve to an existing relative `SPEC.md` path.
4. `call: []` MUST remain when there are no outbound dependencies.
5. Reverse-direction sections and backlink lists MUST NOT be added to called specs.
6. Inbound callers SHOULD be queried instead of written manually.
7. All links MUST be resolved before review.

Exit:

- Calling-side outbound links are complete.
- Inbound callers are derivable from `call` data.

## Optional Interface Contract Authoring

Entry:
`SPEC.md` is approved for scope and linked.

Notes:

- This stage MAY be skipped.
- Optional artifacts SHOULD be created only when they improve clarity, reviewability, or boundary alignment.

Activities:

1. `openapi.yaml` MAY be created for HTTP boundary contracts.
2. `CONTRACT.md` MAY be created for semantic and behavioral contracts.
3. `assets/templates/openapi.yaml` and `assets/templates/CONTRACT.md` MAY be copied when optional artifacts are needed.
4. Generated `CONTRACT.md` content MUST be treated as scaffold content. It MUST be authored before final validation.
5. `CONTRACT.md` MUST use Contract Units when the file exists.
6. `CONTRACT.md` SHOULD map units to SPEC requirements and scenarios.
7. `CONTRACT.md` SHOULD include representative edge cases when they affect reviewer understanding.
8. OpenAPI operations that define `requestBody` MUST define request schemas with required fields and types.
9. OpenAPI responses MUST either define content schemas or intentionally omit content for no-body semantics.
10. Optional artifacts MUST remain consistent with current `SPEC.md` and, when applicable, current `RESEARCH.md`.

Exit:

- Optional contract artifacts are either intentionally skipped or ready for review.

## Spec Review

Entry:
Outputs from SPEC Setup and Document Linking exist.
Optional contract artifacts exist only when the previous stage was used.

Activities:

1. The inline review checklist in `../SKILL.md` MUST be applied.
2. Review evidence MUST follow the contract in [Review Evidence Contract](#review-evidence-contract).
3. If subagent review is available, a separate review agent SHOULD be used.
4. Frontmatter completeness and status transitions MUST be checked.
5. Outbound links and inbound query integrity MUST be verified.
6. If `RESEARCH.md` is relevant, its findings MUST be current enough for this review.
7. If `CONTRACT.md` or `openapi.yaml` exists, those artifacts MUST be checked against SPEC requirements and scenarios.
8. `"${SKILL_ROOT}/scripts/sdd.sh" validate ./spec` MUST be run on the spec root or subtree when `uv` can resolve its runtime and dependency/build artifacts from local cache or local files. When it cannot run, the review record MUST document the runtime blocker and every applicable checklist item MUST be completed manually.
9. When review passes, `SPEC.md` status MUST be updated to `approved` and `last_updated` MUST be refreshed.

Exit:

- Gate 2 applies here.
- Every applicable checklist item is `pass`, `fail`, or `n/a` with rationale.
- The approved artifact set is explicit: `SPEC.md`, and any generated `RESEARCH.md`, `CONTRACT.md`, or `openapi.yaml` that participate in the capability.
- If review fails, the workflow MUST return to the appropriate earlier stage.

## Implementation

Entry:
Gate 2 passed and `SPEC.md` status is `approved`.

Activities:

1. Source changes MUST be made in implementation directories, not in `spec/`.
2. Backup files MUST NOT be created during any workflow stage.
3. `SPEC.md` status MUST be updated to `wip` when implementation starts.
4. Implementation MUST remain aligned to the approved `SPEC.md`.
5. If implementation discovers a spec gap, the relevant spec artifacts MUST be updated first, then Spec Review MUST run again before implementation continues. Spec artifacts describe intended capability, behavior, and constraints. Requirements MUST NOT be derived from current implementation. SPECs SHOULD avoid introducing unnecessary language, framework, library, or code-style constraints unless they were explicitly requested or are materially required.

Exit:

- Implementation satisfies the approved requirements or the workflow has returned for spec revision.

## Implementation Review

Entry:
Implementation is complete and the current artifacts still describe the work.

Activities:

1. The inline review checklist in `../SKILL.md` MUST be applied.
2. Review evidence MUST follow the contract in [Review Evidence Contract](#review-evidence-contract).
3. If subagent review is available, a separate review agent SHOULD be used.
4. Every Functional Requirement MUST map to implementation or to explicit justification in `SPEC.md`.
5. Dependency changes MUST update frontmatter `call` before review closes.
6. Relevant `RESEARCH.md` artifacts MUST remain synchronized with the implemented state.
7. `spec/CHANGELOG.md` MUST be updated when adopted spec-state changes occur. Changes MUST include behavior changes, configuration additions or changes, and contract changes. Research, initialization, planning, and future-reservation content MUST be excluded. Entries MUST keep the latest date first.
8. When review passes, `SPEC.md` status MUST be updated to the correct post-implementation state and `last_updated` MUST be refreshed.
9. `"${SKILL_ROOT}/scripts/sdd.sh" validate ./spec` MUST be re-run on the touched spec root or subtree after the final spec sync when `uv` can resolve its runtime and dependency/build artifacts from local cache or local files. When it cannot run, the review record MUST document the runtime blocker and every applicable checklist item MUST be completed manually.
10. If the consuming repository already uses markdownlint, maintainers MAY re-run `npx -y markdownlint-cli2 <touched-markdown-files>` after the final Markdown sync.

Exit:

- All applicable checklist items are `pass`, `fail`, or `n/a` with rationale.
- `SPEC.md`, `RESEARCH.md`, `CONTRACT.md`, `openapi.yaml`, and `spec/CHANGELOG.md` are synchronized with the implemented state.
- If review fails, the workflow MAY return to Optional Interface Contract Authoring, Spec Review, or Implementation.

## Review Evidence Contract

For v1, reviews MUST be recorded in reviewer or agent output.
The workflow MUST NOT require a repo-tracked `REVIEW.md`.

Each review record MUST include:

- The review type: Spec Review or Implementation Review.
- The artifact scope that was reviewed.
- One result per applicable checklist item.
- Each result expressed as `pass`, `fail`, or `n/a`.
- A short rationale for every applicable checklist item.

## Subagent Invocation Pattern

When subagent review is available, review work SHOULD use a separate agent with read-only intent.

The review agent MUST receive:

- The current `SPEC.md`.
- Any relevant `RESEARCH.md`.
- Any relevant `CONTRACT.md` or `openapi.yaml`.
- The inline review checklist from `../SKILL.md`.
- The required review output format from [Review Evidence Contract](#review-evidence-contract).
