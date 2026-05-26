# SPEC.md Template

Use this template as the canonical shape for generated `SPEC.md` files. Delete optional sections that do not apply. Replace every bracketed placeholder before finalizing the spec, or remove the section when the information is unavailable and not required.

```markdown
---
title: "[Feature or System Name] Specification"
status: draft
created: [YYYY-MM-DD]
updated: [YYYY-MM-DD]
owners:
  - "[person, team, or role]"
---

# [Feature or System Name] Specification

## Summary

[One to three sentences explaining what is being specified and why it matters.]

## Problem

[Describe the current problem, user need, operational gap, or decision pressure. Include evidence when available.]

## Goals

- [Goal 1: concrete outcome]
- [Goal 2: concrete outcome]

## Non-Goals

- [Out-of-scope item 1 and reason]
- [Out-of-scope item 2 and reason]

## Scope

### In Scope

- [Behavior, workflow, user group, system boundary, or deliverable]

### Out of Scope

- [Explicit exclusion not already covered by Non-Goals]

## Users and Stakeholders

- [Primary actor or consuming system]
- [Secondary actor, operator, reviewer, or dependent team]

## Requirements

### Functional Requirements

- FR-001: The system MUST [observable capability].
- FR-002: WHEN [trigger], the system SHALL [observable response].

### Non-Functional Requirements

- NFR-PERF-001: The system MUST [performance target with measurement method].
- NFR-SEC-001: The system MUST [security or privacy requirement].

## Scenarios

### FR-001: [Requirement Title]

#### Scenario: [Successful path]

- GIVEN [precondition]
- WHEN [action or event]
- THEN [observable outcome]

#### Scenario: [Negative or boundary path]

- GIVEN [precondition]
- WHEN [invalid, missing, duplicate, late, or unauthorized input occurs]
- THEN [observable failure handling]

## Acceptance Criteria

- AC-001-A: GIVEN [precondition], WHEN [action or event], THEN [observable outcome].
- AC-001-B: GIVEN [negative or boundary precondition], WHEN [action or event], THEN [observable failure handling].

## Edge Cases and Failure Modes

| Case | Expected Behavior | Requirement |
| --- | --- | --- |
| [Boundary or failure case] | [Expected outcome] | FR-001 |

## Success Criteria

- SC-001: [Measurable outcome with number, timeframe, or verification method].
- SC-002: [Measurable outcome with number, timeframe, or verification method].

## Validated Assumptions

- [Assumption provided by the user, source material, or approved project context]

## Open Questions

| Question | Owner | Needed By | Resolution |
| --- | --- | --- | --- |
| [Question] | [owner] | [date or milestone] | [blank until resolved] |
```

## Optional Sections

Include these sections only when they are required by the request or by the domain.

### API or Interface Contracts

Use when external or internal integrations are part of the requirement. Specify inputs, outputs, error behavior, compatibility expectations, and versioning constraints. Avoid implementation package names unless the integration itself is mandated.

### Data Model

Use when persistent entities, records, identifiers, retention, migration, or data ownership matter. Describe entities, relationships, cardinality, and lifecycle rules.

### UI or UX Behavior

Use when user-visible behavior is part of the requirement. Specify task flow, states, accessibility requirements, empty states, error states, and acceptance criteria. Avoid visual styling unless explicitly requested.

### Design Constraints or Decision Context

Use when the user asks for an RFC-style, SRS-style, or design-aware spec. Capture mandated constraints, decision drivers, considered options, and consequences. Keep design rationale separate from functional requirements.

### Rollout and Migration

Use when the change affects existing users, persisted data, deployments, compatibility, or operational risk. Include phases, gates, rollback triggers, and migration constraints.

### Risks and Mitigations

Use when failure impact is meaningful. Prefer a table with risk, likelihood, impact, detection signal, and mitigation.

### Traceability

Use when requirements must link to sources, tests, decisions, or compliance evidence. Include requirement ID, source, priority, verification method, and status.

### Decision Log

Use when alternatives are considered or decisions need auditability. Include decision, status, date, drivers, considered options, outcome, and consequences.

## Section Rules

- Summary MUST fit in one to three sentences.
- Problem MUST be stated before solution details.
- Goals MUST describe outcomes, not tasks.
- Non-Goals MUST prevent scope creep with explicit exclusions.
- Requirements MUST be testable and traceable.
- Scenarios MUST describe observable behavior.
- Success Criteria MUST be measurable or have a verification method.
- Validated Assumptions MUST come from user-provided context, authorized source material, or explicit approval.
- Open Questions MUST remain separate from requirements.
- Bracketed placeholders MUST be replaced or removed before final output.
