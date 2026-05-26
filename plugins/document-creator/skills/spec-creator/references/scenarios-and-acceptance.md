# Scenarios and Acceptance

Use this reference when converting requirements into testable scenarios, acceptance criteria, success criteria, and conformance tests. Open this reference after functional and non-functional requirements are drafted.

## Scenario Format

Use Given/When/Then structure for any behavior observable by a user, system, operator, or test harness.

```markdown
#### Scenario: [Descriptive Scenario Name]

- GIVEN [precondition or context]
- WHEN [single action, event, or trigger]
- THEN [observable outcome]
- AND [additional observable outcome — optional]
```

Rules:

- `GIVEN` MAY be omitted when no precondition is needed.
- `WHEN` and `THEN` are REQUIRED.
- `AND` extends the preceding clause to add more outcomes from the same action.
- Use plain language, not pseudo-code.
- Each scenario describes one happy or failure path; use separate scenarios for diverging paths.

Good example:

```markdown
#### Scenario: User submits a valid export request

- GIVEN a signed-in user
- WHEN they submit an export request for the last 30 days of data
- THEN the system returns HTTP 201 with a unique job ID
- AND the job enters the "running" state
- AND the system emits a job.created event with the job ID
```

Poor example (too many branches in one scenario):

```markdown
#### Scenario: Export request handling

- GIVEN a user
- WHEN they submit an export request
- THEN the system either creates a job or rejects the request depending on validation
```

## Scenario Coverage

Every functional requirement MUST have at least one scenario or state explicitly why it cannot be scenario-tested.

Example reason: "FR-004: This requirement is platform-specific performance tuning with no observable behavior change; it is verified by profiling, not scenario testing."

Cover these case types when relevant:

| Case Type | Purpose | Example |
| --- | --- | --- |
| Happy path | Valid input and expected success | User submits a valid request; system succeeds |
| Negative path | Invalid, missing, duplicate, expired, late, malformed, unauthorized input | User submits a malformed request; system rejects with error |
| Boundary path | Minimum, maximum, empty, zero, one, many, timeout, size limits | User submits a request for exactly the maximum date range; system accepts |
| Permission path | Role, ownership, authentication, authorization, privacy behavior | User A submits a request for User B's data; system denies access |
| Recovery path | Retry, rollback, fallback, compensation, partial failure behavior | Export job fails midway; system retries and completes |
| State path | Transitions between draft, pending, active, paused, failed, completed, archived, deleted states | Job transitions from running to completed; dependent job auto-starts |

Include cases that matter for the requirement. Omit cases that are out of scope.

## State-Machine Transition Scenarios

When the spec includes a state machine, create explicit scenarios for each transition.

Template:

```markdown
#### Scenario: [Source State] → [Destination State]

- GIVEN the system is in [Source State]
- WHEN [trigger event or condition]
- THEN the system transitions to [Destination State]
- AND [side effect 1: logging, external event, etc.]
- AND [side effect 2: if any]
```

Example:

```markdown
#### Scenario: Running job reaches completion

- GIVEN an export job in the "running" state
- WHEN the job processing completes successfully
- THEN the job transitions to "completed" state
- AND the system emits a job.completed event
- AND the result file is available for download
- AND retention timer starts for the result (30-day expiration)
```

## Idempotency and Recovery Scenarios

When the spec includes retry, recovery, or partial-failure handling, create scenarios showing resumption without duplication.

Template:

```markdown
#### Scenario: Resume after partial failure at step N

- GIVEN a process that completed steps 1 through N-1 successfully
- WHEN the process restarts after a failure at step N
- THEN the process skips steps 1 through N-1
- AND the process resumes at step N without redoing prior steps
- AND [no duplicate side effects from prior steps]
```

Example:

```markdown
#### Scenario: Retry export job after transient network failure

- GIVEN an export job that failed at the "uploading results" step after successfully generating the file
- WHEN the system retries the job
- THEN the system reuses the already-generated file
- AND the system does not regenerate the file
- AND the system resumes uploading the file
- AND the system emits a job.retry event (not a duplicate job.started event)
```

## Acceptance Criteria

Write acceptance criteria as one testable assertion each. Tie criteria to requirement IDs when the spec needs traceability.

```markdown
- AC-001-A: GIVEN a valid export request, WHEN the request is submitted, THEN the system returns HTTP 201 and a job ID.
- AC-001-B: GIVEN an export request outside the allowed date range, WHEN the request is submitted, THEN the system returns HTTP 400 and states the allowed range.
- AC-001-C: GIVEN a user without export permissions, WHEN they submit an export request, THEN the system returns HTTP 403.
```

Rules:

- One assertion per criterion.
- Must be independently testable (no hidden context).
- Must be observable (not "looks good" or "handles errors").
- Tie to a requirement ID when traceability matters.

Anti-patterns:

```markdown
- AC-001-A: The system works correctly.
  - Problem: "works correctly" is unobservable.

- AC-001-B: The system handles errors and logs them.
  - Problem: Too vague; "handles" and "logs them" need specific assertions about format, content, etc.

- AC-001-C: The system is fast and secure.
  - Problem: Vague terms without metrics.
```

## Success Criteria

Success criteria measure whether the feature achieved its intended outcome. They are not the same as implementation tests; they are user, business, operational, or compliance outcomes.

Template:

```markdown
- SC-001: [Measurable outcome with metric, target, measurement window].
```

Good success criteria include:

- A metric or verification method.
- A target value or threshold.
- A measurement window or context when relevant.
- A user, business, operational, or compliance outcome.

Examples:

```markdown
- SC-001: Users can complete the primary export flow in under 2 minutes during moderated usability testing (n >= 5 participants).
- SC-002: At least 95 percent of valid export requests create a job and return a tracking ID within 2 seconds during load testing with concurrent requests.
- SC-003: Support tickets about manual export status checks decrease by 30 percent within 30 days of release.
- SC-004: The system completes all audits without findings related to data segregation or unauthorized access logs.
- SC-005: The system handles up to [throughput] concurrent requests with p95 latency under [SLA], measured over a 1-week production run.
```

Anti-patterns:

```markdown
- SC-001: The feature is working.
  - Problem: Not measurable; no metric or verification method.

- SC-002: 100 percent of export requests succeed.
  - Problem: Unrealistic target; does not account for invalid requests, network failures, or intentional rejections.

- SC-003: The system is fast.
  - Problem: No metric; "fast" is subjective.
```

## Policy Constraints vs. Quality Thresholds

Distinguish hard constraints from measured targets.

| Type | Meaning | Binding | Slip Policy | Example |
| --- | --- | --- | --- | --- |
| Policy constraint | Non-negotiable launch blocker | MUST, MUST NOT | Cannot slip without approval | MUST NOT expose another tenant's data. |
| Quality threshold | Measured target that guides release readiness | SHOULD, target, goal | MAY slip with documented tradeoff | p95 job creation latency SHOULD stay under 2 seconds. |

Never downgrade a policy constraint into a measured target. Never turn every quality threshold into a hard launch blocker without explicit rationale.

Good distinction:

```markdown
## Non-Functional Requirements

- NFR-SEC-001: The system MUST NOT expose another tenant's data in any response or log.
  - Type: Policy constraint (hard blocker)
  - Verification: Code inspection + segregation tests
  - Slip policy: Cannot slip; any exposure is a breaking issue.

- NFR-PERF-001: The system SHOULD keep p95 job creation latency under 2 seconds.
  - Type: Quality threshold (aspirational)
  - Measurement: p95 latency of POST /api/export during load testing
  - Slip policy: MAY exceed under extreme load; documented as a trade-off.
```

## Conformance Test Matrix

Conformance profiles organize tests for implementations. Three standard profiles:

### Core Conformance Profile

Tests that EVERY conforming implementation MUST pass. Cover:

- Mandatory requirements (FR and NFR marked MUST).
- Essential workflows (happy path for each major feature).
- Error handling (negative cases for invalid input, missing data, etc.).
- Boundary behavior (limits, timeouts, concurrency).

### Extension Conformance Profile

Tests for OPTIONAL features. Mark clearly:

```markdown
- [Test name] (IF [Extension Name] is implemented)
  - [Description]

- Export to S3 (IF S3 Storage Extension is implemented)
  - GIVEN export job configured to write to S3
  - WHEN job completes
  - THEN result file is written to the configured S3 bucket with the correct credentials
```

### Real Integration Profile

Integration tests against live external systems (not mocks). Example:

```markdown
- Real Tracker Integration (Linear API)
  - Fetch candidate issues from a real Linear project
  - Verify issue fields are normalized correctly
  - Verify pagination handles > 50 issues
```

Example matrix:

```markdown
## Test and Validation Matrix

### Core Conformance Profile (REQUIRED)

- Export Job Creation
  - Test: Valid request creates job and returns 201
  - Test: Invalid request returns 400 with validation error
  - Test: Missing required field returns 400
  - Test: Exceeded date range limit returns 400

- Job Status Polling
  - Test: Running job returns progress updates
  - Test: Completed job returns result metadata
  - Test: Failed job returns error message

- Data Segregation
  - Test: User A cannot access User B's job
  - Test: Deleted job data is not accessible

### Extension Conformance Profile (IF Webhook Extension is implemented)

- Webhook Delivery
  - Test: Completed job triggers configured webhook
  - Test: Webhook retry on transient failure
  - Test: Webhook timeout after [duration]

### Real Integration Profile

- Linear Tracker Integration
  - Test: Fetch issues from real Linear project
  - Test: Verify all issue fields normalize correctly
  - Test: Pagination works for > 50 issues
```

## Open Questions Template

Open questions must be specific enough for a human to answer.

```markdown
| Question | Owner | Needed By | Resolution |
| --- | --- | --- | --- |
| [Specific, answerable question] | [Name or Role] | [Date or Milestone] | [blank until resolved] |
```

Good questions:

```markdown
| What is the maximum date range allowed per export request? | Product | Sprint 3 Planning | 90 days (approved) |
| Should the system auto-delete expired export results or require manual cleanup? | Tech Lead | Design Phase | Auto-delete after 30 days |
| What is the acceptable latency for job status polling in the UI? | UX Lead | Refinement | p95 under 500ms |
```

Poor questions:

```markdown
| Export stuff | ? | TBD | ? |
| What about performance? | ? | ? | ? |
```

Guidance:

- Limit active clarification markers to the highest-impact unknowns.
- Priority order: scope, security/privacy, user experience, then technical details.
- If a question is not resolved by the time writing is complete, keep it in the Open Questions section; never hide an unresolved guess in a requirement.

## Scenario-to-Test Traceability

When mapping scenarios to test cases, maintain clear traceability:

```markdown
| Requirement | Scenario | Test Case | Owner | Status |
| --- | --- | --- | --- | --- |
| FR-001 | User submits valid request | test_export_valid_request | @alice | Passing |
| FR-001 | User submits invalid request | test_export_invalid_request | @alice | Passing |
| FR-002 | Job status polling | test_job_status_polling | @bob | Pending |
| NFR-PERF-001 | P95 latency under 2s | perf_test_export_latency | @charlie | Pending |
```

Use this table to:

- Ensure every requirement has test coverage.
- Prevent orphan scenarios without implementing tests.
- Track test implementation status.
