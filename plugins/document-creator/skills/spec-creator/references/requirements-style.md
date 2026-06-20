# Requirements Style

Use this reference when writing or reviewing individual requirements inside a `SPEC.md`.
Open this reference when the spec structure is in place and you need to draft individual functional, non-functional, and acceptance requirements.

## Requirement IDs

Use stable IDs that remain valid even if requirements are reordered.
Do not reuse IDs after deleting or replacing a requirement.

| Prefix | Use | Example |
| --- | --- | --- |
| `FR-001` | Functional requirement | FR-001: The system MUST accept email addresses. |
| `NFR-PERF-001` | Performance requirement | NFR-PERF-001: The system MUST process exports within 2 seconds. |
| `NFR-SEC-001` | Security or privacy requirement | NFR-SEC-001: The system MUST NOT expose tenant data. |
| `NFR-REL-001` | Reliability or availability requirement | NFR-REL-001: The service MUST recover from transient failures. |
| `NFR-OBS-001` | Observability requirement | NFR-OBS-001: The system MUST emit structured logs. |
| `NFR-UX-001` | Usability or accessibility requirement | NFR-UX-001: Dialogs MUST be keyboard-navigable. |
| `NFR-COMPAT-001` | Compatibility or version requirement | NFR-COMPAT-001: The system MUST support Python 3.8+. |
| `AC-001-A` | Acceptance criterion tied to requirement 001 | AC-001-A: GIVEN valid input, WHEN submitted, THEN output is returned. |
| `SC-001` | Success criterion (not requirement-specific) | SC-001: Users complete the flow in under 2 minutes. |

Historical traceability: When a requirement is deleted, mark it in a change log or delta spec section if traceability matters.
Do not reuse the ID.

## Normative Language

Use RFC 2119 language deliberately.
Choose MUST versus SHOULD based on actual constraint, not habit.

| Keyword | Interpretation | Use When |
| --- | --- | --- |
| `MUST` or `SHALL` | Absolute requirement; implementation must obey | Real constraint that cannot be violated |
| `MUST NOT` or `SHALL NOT` | Absolute prohibition | Unsafe, insecure, or breaking behavior |
| `SHOULD` | Recommended unless justified exception | Strong preference but implementation may diverge if documented |
| `SHOULD NOT` | Discouraged unless justified exception | Likely problematic but not forbidden |
| `MAY` | Optional behavior | Implementation can choose to do it or not |
| `REQUIRED` | Absolute (RFC synonym for MUST) | When matching formal RFC or protocol style |
| `RECOMMENDED` | Advised but optional | When matching formal RFC or protocol style |
| `OPTIONAL` | No requirement to implement | Extensions, nice-to-haves |

Guidance: Prefer `MUST` for product requirements, `SHALL` for formal contracts or protocol specs.
Use `SHOULD` when the policy is aspirational but implementers may diverge.
Always document the divergence policy.

## EARS Patterns

Use EARS (Easy Approach to Requirements Syntax) sentence patterns to reduce ambiguity.
Each pattern targets a different trigger or context.

| Pattern | Form | Use | Example |
| --- | --- | --- | --- |
| Ubiquitous | The system SHALL [behavior]. | Always-active behavior, no special trigger | The system SHALL validate email format on input. |
| Event-driven | WHEN [trigger], the system SHALL [response]. | Behavior caused by an external event | WHEN a user clicks Export, the system SHALL create a job. |
| State-driven | WHILE [state], the system SHALL [behavior]. | Behavior active during a particular state | WHILE a job is running, the system SHALL report progress. |
| Unwanted behavior | IF [condition], THEN the system SHALL [response]. | Error, abuse, or failure handling | IF a password contains fewer than 8 chars, the system SHALL reject it. |
| Optional feature | WHERE [feature is enabled], the system SHALL [behavior]. | Feature-flagged or conditional behavior | WHERE webhooks are enabled, the system SHALL POST to the configured URL. |

## Implementation-Defined Behavior

Define what "implementation-defined" means in your spec, then use it consistently for contract surface the spec does not prescribe universally.
Every implementation-defined choice MUST be documented by the implementer.

Template sentence:

`[Field Name] (type), default implementation-defined - the implementation MUST document the selected behavior.`

Example:

`approval_policy (string), default implementation-defined - implementations MUST document whether they auto-approve commands, require operator confirmation, or fail approval-required turns.`

Rationale: Symphony uses this pattern to allow multiple trust postures (high-trust auto-approval vs.
Strict sandboxing) while keeping the contract clear.

## Good Requirement Shape

Write one observable behavior per requirement.
Each requirement MUST be testable or inspectable.

```markdown
- FR-001: WHEN a signed-in user submits a valid export request, the system MUST create an export job and return a unique tracking identifier.
- FR-002: IF an export request includes a date range larger than 90 days, THEN the system MUST reject the request with an error message naming the maximum allowed range.
- FR-003: WHILE an export job is running, the system MUST report its progress to any client polling the status endpoint.
- NFR-PERF-001: The system MUST create an export job within 2 seconds for 95 percent of valid requests, measured at the service boundary under normal load.
- NFR-SEC-001: The system MUST NOT expose another tenant's data in any API response or log output.
```

Each requirement above:

- Describes one observable behavior.
- Uses EARS pattern (WHEN, IF/THEN, WHILE) or ubiquitous form.
- Includes context (request type, user role, state) when relevant.
- Names metrics (2 seconds, 95 percent, 90 days) explicitly.

## Poor Requirement Shape

Avoid vague, compound, or implementation-led requirements.

```markdown
- FR-001: The system should handle exports well.
  - Problems: "well" is vague, "handle" is too broad, no measurable criteria.
- FR-002: Use Redis and a worker queue to process exports.
  - Problems: Implementation detail (Redis, worker queue) not observable behavior; prescribes HOW, not WHAT.
- FR-003: The export feature must be fast, secure, intuitive, and scalable.
  - Problems: Vague terms (fast, secure, intuitive, scalable) without metrics; compound requirement joined by "and".
- FR-004: Export jobs that fail SHOULD retry automatically and MUST support rollback.
  - Problems: Two separate behaviors in one requirement; "support rollback" is too vague.
```

Each problem above violates one rule: observable behavior, no implementation detail unless mandated, no vague terms, one behavior per requirement.

## Requirement Rules

- One observable behavior per requirement: Every requirement MUST describe a behavior a test harness, observer, or user can detect.
- No compound requirements: Do not join multiple behaviors with "and".
  - Split into separate requirements.
- No implementation detail: Do not name libraries, tools, or algorithms unless the implementation is a mandated constraint (e.g., "MUST use TLS 1.2").
- Name limits and units: When a requirement mentions a number, include units (seconds, percent, bytes, days, etc.).
  - Avoid "many", "few", "large", "small".
- Functional requirement traceability: Every FR MUST link to at least one scenario or state explicitly why it cannot be scenario-tested.
- Non-functional requirement metrics: Every NFR SHOULD include a metric, measurement point (where/how to measure), and verification method (load test, audit, inspection, etc.).
- Unknowns become open questions: If a fact is missing, write an open question.
  - Do not hide unresolved guesses inside requirement text.
- No silent assumptions: Every assumption MUST be captured in the Validated Assumptions section or marked as an open question.

## Requirement Patterns by Category

### Functional Requirements (WHAT the system does)

Form: Ubiquitous, Event-driven, State-driven, Unwanted behavior, or Optional feature.

```markdown
- FR-001: The system MUST accept POST requests to /api/export with a JSON body.
- FR-002: WHEN a POST request is submitted, the system SHALL validate the request schema against [schema reference].
- FR-003: IF validation fails, THEN the system SHALL return HTTP 400 with a JSON error object including the failed fields.
- FR-004: IF validation succeeds, THEN the system SHALL create an export job and return HTTP 201 with the job ID.
```

### Non-Functional Requirements (HOW well the system does it)

Form: Metric + measurement point + verification method.

```markdown
- NFR-PERF-001: The system MUST create an export job within 2 seconds for 95 percent of valid requests measured at the service boundary under normal load.
  - Measurement: p95 latency of POST /api/export.
  - Verification: Load test with [load profile] and measure with [APM tool].

- NFR-SEC-001: The system MUST NOT expose another tenant's data in any API response, log output, or error message.
  - Measurement: Code inspection + end-to-end tests.
  - Verification: [Data segregation tests] MUST pass; audit logs MUST show no cross-tenant access.

- NFR-REL-001: The system MUST recover from transient network failures and retry failed upstream calls up to 3 times with exponential backoff.
  - Measurement: Upstream failure injection + observation of retry behavior.
  - Verification: Chaos test: block upstream API, observe retries logged with exponential delay.
```

Each NFR includes a metric (seconds, percent, count) and a specific verification method.

### Acceptance Criteria (Testable assertions for a requirement)

Form: GIVEN/WHEN/THEN with one assertion per criterion.

```markdown
- AC-001-A: GIVEN a valid export request, WHEN the request is submitted, THEN the system returns HTTP 201 and a job ID.
- AC-001-B: GIVEN an export request with a missing required field, WHEN the request is submitted, THEN the system returns HTTP 400 with an error message.
- AC-001-C: GIVEN an authenticated user, WHEN they retrieve the job status, THEN the system returns only their own job data.
```

Each criterion is independently testable and ties to a requirement.

## Delta Requirements (Updates to Existing Specs)

When modifying an existing spec, organize changes into sections:

```markdown
## Added Requirements

- FR-004: WHEN [new trigger], the system SHALL [new behavior].

## Modified Requirements

- FR-002: [Full updated requirement text].
  - Previously: [old text].
  - Reason: [business/technical reason for the change].

## Removed Requirements

- FR-003: [removed requirement title].
  - Reason: [why it was removed].
  - Migration: [what replaces it, if anything].
```

Use delta sections when review clarity matters.
After approval, fold deltas into the canonical spec so the spec remains a clean, current source of truth.

## Priority and Mandatory Distinction

Not every requirement is equally critical.
Mark priorities when they matter:

```markdown
## Functional Requirements

- P0 (Launch Blocker)
  - FR-001: The system MUST [critical behavior].
  - FR-002: The system MUST [security requirement].

- P1 (High Priority)
  - FR-003: The system SHOULD [important feature].

- P2 (Nice to Have)
  - FR-004: The system MAY [optional enhancement].
```

Rationale: Launch blockers (security, core workflow) are non-negotiable.
High-priority items improve usability.
Nice-to-haves can slip if time is constrained.

## Validation Checklist for Requirements

Before finalizing a requirement:

- [ ] Is it one observable behavior, not two joined by "and"?
- [ ] Does it use RFC 2119 language (MUST / SHOULD / MAY)?
- [ ] Is it testable or inspectable?
- [ ] If functional, does it have at least one scenario or a stated reason it cannot?
- [ ] If non-functional, does it include a metric and measurement point?
- [ ] Are vague terms (fast, robust, intuitive) either removed or quantified?
- [ ] Are limits and units named explicitly (seconds, percent, bytes, days)?
- [ ] Does it avoid implementation detail unless mandated?
- [ ] Is it traceable to a goal, decision, or open question?
