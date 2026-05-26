# Scenarios and Acceptance

Use this guide to convert requirements into testable scenarios and acceptance criteria.

## Scenario Format

Use Given/When/Then for behavior that can be observed by a user, system, operator, or test harness.

```markdown
#### Scenario: [ScenarioName]

- GIVEN [precondition or context]
- WHEN [single action, event, or trigger]
- THEN [observable outcome]
- AND [additional observable outcome]
```

`GIVEN` MAY be omitted when no precondition is needed. `WHEN` and `THEN` are required. `AND` extends the preceding clause.

## Scenario Coverage

Every functional requirement MUST have at least one scenario or state why it cannot be scenario-tested. Add more scenarios when meaningful behavior differs by role, state, permission, input validity, or failure mode.

Cover these case types when relevant:

- Happy path: valid input and expected success.
- Negative path: invalid, missing, duplicate, expired, late, malformed, or unauthorized input.
- Boundary path: minimum, maximum, empty, zero, one, many, timeout, and size limits.
- Permission path: role, ownership, authentication, authorization, or privacy behavior.
- Recovery path: retry, rollback, fallback, compensation, or partial failure behavior.
- State path: transitions between draft, pending, active, paused, failed, completed, archived, or deleted states.

## Acceptance Criteria

Write acceptance criteria as one testable assertion each. Tie criteria to requirement IDs when the spec needs traceability.

```markdown
- AC-001-A: GIVEN a valid export request, WHEN the request is submitted, THEN the system returns a tracking identifier.
- AC-001-B: GIVEN an export request outside the allowed date range, WHEN the request is submitted, THEN the system rejects it and states the allowed range.
```

Do not write acceptance criteria such as "works correctly", "looks good", "is intuitive", or "handles errors" without specifying observable behavior.

## Success Criteria

Success criteria measure whether the feature achieved its intended outcome. They are not the same as implementation tests.

Good success criteria include:

- A metric or verification method.
- A target value or threshold.
- A measurement window or context when relevant.
- A user, business, operational, or compliance outcome.

```markdown
- SC-001: Users can complete the primary export flow in under 2 minutes during usability review.
- SC-002: At least 95 percent of valid export requests create a tracking identifier within 2 seconds during load testing.
- SC-003: Support tickets about manual export status checks decrease by 30 percent within 30 days of release.
```

## Quality Thresholds and Policy Constraints

Separate hard constraints from measurable targets.

| Type | Meaning | Example |
| --- | --- | --- |
| Policy constraint | Non-negotiable launch blocker | The system MUST NOT expose another tenant's data. |
| Quality threshold | Measured target that guides release readiness | The system SHOULD keep p95 job creation latency under 2 seconds. |

Do not downgrade a policy constraint into a target. Do not turn every quality threshold into a launch blocker without explicit rationale.

## Open Questions

Open questions must be specific enough for a human to answer.

Poor question:

```markdown
- [NEEDS CLARIFICATION: export stuff]
```

Good question:

```markdown
- [NEEDS CLARIFICATION: What is the maximum date range allowed for a single export request?]
```

Limit active clarification markers to the highest-impact unknowns. Use priority order: scope, security/privacy, user experience, then technical details.
