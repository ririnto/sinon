# System-Scale SPEC.md Template

Open this reference when authoring a system-scale SPEC.md. System-scale applies to services, daemons, protocol implementations, agent runtimes, multi-component pipelines, or anything requiring a state machine, reference algorithms, or a conformance matrix. For feature-scale specifications, use the mini template inside `SKILL.md`.

Copy the skeleton below, replace bracketed placeholders, and delete sections that do not apply. Keep sections in the numbered order shown; this order matches the Symphony specification structure and is optimized for reader understanding.

````markdown
---
title: "[System or Service Name] Specification"
status: draft
created: [YYYY-MM-DD]
updated: [YYYY-MM-DD]
owners:
  - "[person, team, or role]"
version: "[version or omit if unversioned]"
---

# [System or Service Name] Specification

## Summary

[One to three sentences explaining what is being specified and why it matters.]

## Normative Language

The key words `MUST`, `MUST NOT`, `REQUIRED`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, and `OPTIONAL` in this document are to be interpreted as described in RFC 2119.

`Implementation-defined` means the behavior is part of the implementation contract, but this specification does not prescribe one universal policy. Implementations MUST document the selected behavior. Example: "approval policy (type string), default implementation-defined — the implementation MUST document whether it auto-approves, requires operator confirmation, or fails user-input-required turns."

## 1. Problem Statement

[Describe the current problem, user need, operational gap, or decision pressure. Include evidence when available.]

Important boundary:

- [State what this system IS and clearly mark what it is NOT. Example: "Symphony is a scheduler/runner and tracker reader. It is not a distributed job queue or a code hosting service."]

## 2. Goals and Non-Goals

### 2.1 Goals

- [Concrete outcome 1]
- [Concrete outcome 2]
- [Concrete outcome 3]

### 2.2 Non-Goals

- [Out-of-scope item 1 and why]
- [Out-of-scope item 2 and why]

## 3. Scope

### In Scope

- [Behavior, workflow, user group, system boundary, or deliverable]

### Out of Scope

- [Explicit exclusion not already covered by Non-Goals]

## 4. Users and Stakeholders

- [Primary actor or consuming system]
- [Secondary actor, operator, reviewer, or dependent team]

## 5. System Overview

### 5.1 Main Components

1. `[Component Name]`
   - Input: [what it receives]
   - Responsibility: [what it does]
   - Output: [what it produces]

2. `[Component Name]`
   - Input: [what it receives]
   - Responsibility: [what it does]
   - Output: [what it produces]

### 5.2 Abstraction Levels

The system is organized into these layers:

1. `Policy Layer` (repo-defined)
   - [Team-specific rules, prompt body, configuration policy]

2. `Configuration Layer` (typed getters)
   - [Parses, validates, provides access to config]

3. `Coordination Layer` (orchestrator)
   - [Polling loop, eligibility, concurrency, retries, reconciliation]

4. `Execution Layer` (workspace + subprocess)
   - [Filesystem lifecycle, subprocess execution, process management]

5. `Integration Layer` (external systems)
   - [API calls, data normalization, transport handling]

6. `Observability Layer` (logs + status)
   - [Operator visibility, structured logs, status surfaces]

### 5.3 External Dependencies

- [External service / library / protocol] — purpose and version constraints
- [External service / library / protocol] — purpose and version constraints

## 6. Core Domain Model

### 6.1 Entities

#### 6.1.1 [Entity Name]

[Brief description]

Fields:

- `field_name` (type)
  - [Description, constraints, nullability]
- `field_name` (type)
  - [Description, constraints, nullability]

#### 6.1.2 [Entity Name]

[Brief description]

Fields:

- `field_name` (type)
  - [Description, constraints, nullability]

### 6.2 Stable Identifiers and Normalization Rules

- `Identifier Name`
  - Use case: [what it identifies]
  - Format: [string format, UUID, etc.]
- `Identifier Name`
  - Use case: [what it identifies]
  - Format: [string format, UUID, etc.]

Normalization:

- [Rule 1: how to normalize field X]
- [Rule 2: how to normalize field Y]

## 7. Configuration Specification

### 7.1 Configuration Resolution Pipeline

Configuration is resolved in this order:

1. [Step 1 — select source, file path, or environment]
2. [Step 2 — parse or deserialize]
3. [Step 3 — apply defaults]
4. [Step 4 — validate]

### 7.2 Dynamic Reload Semantics

- The software MUST detect changes to [config file / settings].
- On change, it MUST re-read and re-apply [which config fields] without restart.
- The software MUST attempt to adjust live behavior [list behaviors affected by reload].
- Invalid reloads MUST NOT crash the service; keep operating with the last known good config.

### 7.3 Preflight Validation

Validation runs at [startup / per-tick / before dispatch].

Checks:

- [Validation 1 — what is checked and when]
- [Validation 2 — what is checked and when]

Failure behavior:

- [Fail startup / skip dispatch / log and continue]

### 7.4 Core Config Fields Summary (Cheat Sheet)

This section is intentionally redundant for implementation speed.

- `field.name` (type)
  - Default: [value or "implementation-defined"]
  - Constraints: [range, format, allowed values]
  - Reload semantics: [dynamic / requires restart]

## 8. State Machine

### 8.1 Named States

1. `StateName`
   - Description: [what the system is doing in this state]
   - Invariants: [what must be true]

2. `StateName`
   - Description: [what the system is doing in this state]
   - Invariants: [what must be true]

### 8.2 Lifecycle Phases

A typical execution transitions through these phases:

1. `Phase Name` — [what happens]
2. `Phase Name` — [what happens]
3. `Phase Name` — [what happens]

### 8.3 Transition Triggers and Rules

- `Trigger Name`
  - Precondition: [state or condition]
  - Action: [what happens]
  - Next state: [resulting state]

- `Trigger Name`
  - Precondition: [state or condition]
  - Action: [what happens]
  - Next state: [resulting state]

### 8.4 Idempotency and Recovery Rules

- [Rule 1: how to prevent duplicate execution or side effects]
- [Rule 2: how to resume after partial failure]
- [Rule 3: recovery contract for restart]

## 9. Scheduling and Reconciliation

### 9.1 Poll Loop

At startup, the service [does what]. Then every [interval]:

1. [Step 1 — reconcile state]
2. [Step 2 — validate configuration]
3. [Step 3 — fetch candidates]
4. [Step 4 — dispatch work]
5. [Step 5 — emit status updates]

### 9.2 Candidate Selection Rules

An item is dispatch-eligible only if all are true:

- [Criterion 1]
- [Criterion 2]
- [Criterion 3]

### 9.3 Concurrency Control

- Global limit: [formula or policy]
- Per-category limit: [formula or policy]
- Measurement: [how are counts tracked]

### 9.4 Retry and Backoff

Normal continuation retry:

- Delay: [time or formula]
- Trigger: [when it happens]

Failure-driven retry:

- Formula: `delay = min([base] * 2^(attempt - 1), [max])`
- Backoff caps at: [time limit]
- Max attempts: [limit or unbounded]

### 9.5 Active Reconciliation

Reconciliation runs [frequency] and detects:

- [Condition 1 — action when detected]
- [Condition 2 — action when detected]

State refresh:

- Fetch [what data]
- Update [which fields]
- Terminate [under what condition]

### 9.6 Startup Cleanup

When the service starts:

1. [Step 1]
2. [Step 2]

## 10. Resource Management and Safety

### 10.1 Workspace Layout

- Root: [path notation]
- Per-item path: [formula for derived paths]
- Persistence: [are workspaces preserved or cleaned]

### 10.2 Workspace Creation and Reuse

Input: [what identifies a workspace]

Algorithm:

1. [Step 1]
2. [Step 2]
3. [Step 3]

### 10.3 Workspace Hooks

Supported hooks:

- `hook_name` — [when it runs]
- `hook_name` — [when it runs]

Execution:

- Shell context: [sh / bash / OS-specific]
- Working directory: [workspace path]
- Timeout: [duration or config field]

Failure semantics:

- `hook_name` failure is [fatal / logged-only]

### 10.4 Safety Invariants

Invariant 1: [statement of constraint]

- Validation: [how it is enforced]

Invariant 2: [statement of constraint]

- Validation: [how it is enforced]

## 11. Integration Protocol

### 11.1 Launch Contract

Subprocess launch:

- Command: [template or reference to config field]
- Invocation: [how the command is invoked, e.g., bash -lc]
- Working directory: [what cwd is set to]
- Transport: [protocol or framing]

### 11.2 Session Startup Responsibilities

The client MUST:

- [Requirement 1]
- [Requirement 2]
- [Requirement 3]

### 11.3 Streaming Message Processing

Message types:

- `message_type` — [meaning]
- `message_type` — [meaning]

Completion conditions:

- [Protocol success signal] → success
- [Protocol failure signal] → failure
- [Timeout trigger] → failure

### 11.4 Emitted Events

The subprocess emits structured events including:

- `event_type` — [when and what it means]
- `event_type` — [when and what it means]

### 11.5 Approval, Tool Calls, and Input Policy

Behavior is implementation-defined.

Requirements:

- Implementations MUST document the chosen [approval / sandbox / input] policy.
- [Policy behavior 1]
- [Policy behavior 2]

### 11.6 Timeouts and Error Mapping

Timeouts:

- `timeout_name`: [duration] — [what triggers it]
- `timeout_name`: [duration] — [what triggers it]

Error categories:

- `error_class_name` — [when it occurs and recovery behavior]
- `error_class_name` — [when it occurs and recovery behavior]

### 11.7 Runner Contract

The `[Component Name]` wraps [inputs] and [responsibilities].

Behavior:

1. [Step 1]
2. [Step 2]
3. [Step 3]

## 12. External System Integration Contract

### 12.1 REQUIRED Operations

An implementation MUST support:

1. `operation_name(inputs)`
   - Returns: [output shape]
   - Used for: [why and when]

2. `operation_name(inputs)`
   - Returns: [output shape]
   - Used for: [why and when]

### 12.2 Query Semantics

[System-specific requirements for calls to external systems]

- [Requirement 1]
- [Requirement 2]
- [Pagination / filtering / sorting rules]

### 12.3 Normalization Rules

Responses are normalized to match Section 6 (Domain Model):

- [Normalization rule 1]
- [Normalization rule 2]

### 12.4 Error Handling Contract

Error categories:

- `error_class_name` — [meaning]
- `error_class_name` — [meaning]

Behavior on error:

- [Error class 1]: [action — log and skip / log and retry / fail loudly]
- [Error class 2]: [action — log and skip / log and retry / fail loudly]

### 12.5 Write Boundaries

[Clarify what this system writes vs. what it reads. Example: "Symphony is a reader only; ticket mutations are handled by the agent."]

## 13. Prompt or Context Assembly (OPTIONAL)

### 13.1 Inputs

Inputs to [context rendering / prompt building]:

- [Input 1]
- [Input 2]

### 13.2 Rendering Rules

- [Rule 1 — how variables/templates are processed]
- [Rule 2 — strictness of variable/filter checking]
- [Rule 3 — how to handle nested data]

### 13.3 Retry / Continuation Semantics

[Description of how context differs on retry vs. first attempt vs. continuation]

### 13.4 Failure Semantics

If [context rendering] fails:

- [Action — fail the operation / retry / use fallback]

## 14. Functional Requirements

- FR-001: [Observable capability or behavior]
- FR-002: WHEN [trigger], the system SHALL [observable response].
- FR-003: IF [condition], THEN the system MUST [observable failure handling].

## 15. Non-Functional Requirements

### Performance

- NFR-PERF-001: [Measurable performance target with verification method]
- NFR-PERF-002: [Measurable performance target with verification method]

### Security and Privacy

- NFR-SEC-001: [Security or privacy requirement with verification method]
- NFR-SEC-002: [Security or privacy requirement with verification method]

### Reliability and Availability

- NFR-REL-001: [Reliability requirement with measurement method]
- NFR-REL-002: [Availability requirement with measurement method]

### Observability

- NFR-OBS-001: [Observability requirement]
- NFR-OBS-002: [Observability requirement]

### Usability and Compatibility

- NFR-UX-001: [Usability or accessibility requirement]
- NFR-COMPAT-001: [Compatibility or version requirement]

## 16. Scenarios

### FR-001: [Requirement Title]

#### Scenario: [Successful path]

- GIVEN [precondition]
- WHEN [action or event]
- THEN [observable outcome]

#### Scenario: [Negative or boundary path]

- GIVEN [precondition]
- WHEN [invalid or edge-case input]
- THEN [observable failure handling]

## 17. Acceptance Criteria

- AC-001-A: GIVEN [precondition], WHEN [action], THEN [observable outcome].
- AC-001-B: GIVEN [negative precondition], WHEN [action], THEN [observable failure handling].
- AC-002-A: GIVEN [state X], WHEN [trigger Y], THEN [observable state transition Z].

## 18. Edge Cases and Failure Modes

| Case | Expected Behavior | Requirement |
| --- | --- | --- |
| [Boundary or failure case] | [Expected outcome] | FR-001 |
| [Failure scenario] | [Expected recovery or logging] | FR-002 |

## 19. Success Criteria

- SC-001: [Measurable outcome with number, timeframe, or verification method].
- SC-002: [Measurable outcome with number, timeframe, or verification method].
- SC-003: [Measurable operational outcome].

## 20. Logging, Status, and Observability

### 20.1 Logging Conventions

REQUIRED context fields:

- [field_name] — [description]
- [field_name] — [description]

Message formatting:

- Use `key=value` phrasing.
- Include outcome (`completed`, `failed`, `retrying`, etc.).
- Include concise failure reason when present.

### 20.2 Logging Sinks

[The spec does not prescribe where logs are written.]

Requirements:

- Operators MUST be able to see [startup / critical] failures without [debugging / attaching].
- Implementations MAY write to [one or more sinks — specify which].

### 20.3 Runtime Snapshot / Monitoring Interface (OPTIONAL but RECOMMENDED)

If exposed, return:

- `running` — list of running session rows with `[field_name]` per row
- `retrying` — list of retry queue rows
- `[aggregate_name]` — aggregate counters (tokens, duration, etc.)
- `rate_limits` — latest external rate-limit snapshot

### 20.4 Status Surface (OPTIONAL)

[Human-readable status output is OPTIONAL and implementation-defined.]

Example:

- Terminal output with current running items
- Dashboard URL with live update
- [Custom status interface]

## 21. Failure Model and Recovery

### 21.1 Failure Classes

1. `FailureClass`
   - Trigger: [what causes it]
   - Impact: [what breaks or degrades]
   - Recovery: [how to recover]
   - Operator intervention: [if needed]

2. `FailureClass`
   - Trigger: [what causes it]
   - Impact: [what breaks or degrades]
   - Recovery: [how to recover]
   - Operator intervention: [if needed]

### 21.2 Recovery Behavior

- [Failure class 1]: [Recovery action — retry / fail loudly / fallback]
- [Failure class 2]: [Recovery action — retry / fail loudly / fallback]

### 21.3 Partial-State Recovery on Restart

After an unclean restart:

1. [Step 1 — detect partial state]
2. [Step 2 — recover or reset]

### 21.4 Operator Intervention Points

Situations requiring human action:

- [Intervention 1: symptom, decision, action]
- [Intervention 2: symptom, decision, action]

## 22. Security and Operational Safety

### 22.1 Trust Boundary

[Statement of what is trusted and what is not. Example: "The system trusts the WORKFLOW.md file checked into the repository and the configured issue tracker API. It does not trust agent output when executing file operations outside the workspace."]

### 22.2 Filesystem Safety

- [Safety rule 1]
- [Safety rule 2]
- [Validation how — path normalization, prefix check, etc.]

### 22.3 Secret Handling

[How are API keys, credentials, tokens handled — environment variables, config fields, in-memory, etc.]

- [Requirement 1]
- [Requirement 2]

### 22.4 Hook Script Safety

[If hooks are supported — how are they executed and what are the safety constraints]

- [Constraint 1]
- [Constraint 2]

### 22.5 Harness Hardening

[Any additional hardening, sandboxing, or security-hardened setup]

## 23. Reference Algorithms

### 23.1 Startup

```text
function startup():
    [Step 1]
    [Step 2]
    [Step 3]
    return ready
end
```

### 23.2 Main Polling Tick

```text
function poll_tick():
    reconcile_running_items()
    validate_configuration()
    candidates = fetch_active_candidates()
    candidates = sort_by_priority(candidates)
    for each candidate in candidates:
        if available_slots() > 0:
            dispatch_worker(candidate)
    emit_status_updates()
end
```

### 23.3 Reconciliation

```text
function reconcile_running():
    for each running_item in running_map:
        elapsed = now - last_event_timestamp
        if elapsed > stall_timeout:
            terminate_worker(running_item, "stalled")
        else:
            current_state = fetch_item_state(running_item.id)
            if current_state in terminal_states:
                terminate_and_cleanup(running_item)
end
```

### 23.4 Retry Scheduling

```text
function schedule_retry(item_id, attempt, error):
    cancel_existing_retry(item_id)
    if normal_exit(error):
        delay_ms = 1000
    else:
        delay_ms = min(10000 * 2^(attempt - 1), max_backoff_ms)
    due_at = now + delay_ms
    store_retry_entry(item_id, attempt, due_at)
end
```

## 24. Test and Validation Matrix

### 24.1 Core Conformance Profile

Tests that every conforming implementation MUST pass:

- [Test area 1]
  - [Test 1]
  - [Test 2]
- [Test area 2]
  - [Test 1]
  - [Test 2]

### 24.2 Extension Conformance Profile (If [Extension] is implemented)

- [Test area 1]
  - [Test 1]
  - [Test 2]

### 24.3 Real Integration Profile

Integration tests against live external systems:

- [Test 1 — against live external system]
- [Test 2 — against live external system]

## 25. Implementation Checklist (Definition of Done)

### 25.1 REQUIRED for Conformance

- [ ] [REQUIRED item 1]
- [ ] [REQUIRED item 2]
- [ ] [REQUIRED item 3]

### 25.2 RECOMMENDED Extensions

- [ ] [Extension 1]
- [ ] [Extension 2]

### 25.3 Operational Validation Before Production

- [ ] [Validation 1]
- [ ] [Validation 2]

## 26. Validated Assumptions

- [Assumption 1: source or approval]
- [Assumption 2: source or approval]

## 27. Open Questions

| Question | Owner | Needed By | Resolution |
| --- | --- | --- | --- |
| [Question 1] | [owner] | [date or milestone] | [blank until resolved] |
| [Question 2] | [owner] | [date or milestone] | [blank until resolved] |

## 28. Decision Log (OPTIONAL)

| Decision | Status | Date | Drivers | Considered Options | Outcome | Consequences |
| --- | --- | --- | --- | --- | --- | --- |
| [Decision 1] | [resolved/pending] | [date] | [why] | [alternatives] | [chosen path] | [impacts] |

## 29. Rollout / Migration (OPTIONAL)

[If this change affects existing users, data, or deployments, describe phases, gates, and rollback triggers.]

## 30. Risks and Mitigations (OPTIONAL)

| Risk | Likelihood | Impact | Detection | Mitigation |
| --- | --- | --- | --- | --- |
| [Risk 1] | [High/Med/Low] | [High/Med/Low] | [how to detect] | [how to mitigate] |

## Appendix A. OPTIONAL Extension Profile

Extensions are additional capabilities beyond core conformance.

### Extension: [Extension Name]

[Description of what the extension adds.]

Conformance requirement:

- [Test 1 — REQUIRED when extension is implemented]
- [Test 2 — REQUIRED when extension is implemented]

[Additional details as needed.]
````

## Section Rules

- Every section in the template addresses one coherent topic.
- Sections are numbered for stable cross-reference.
- Bracketed placeholders `[text]` indicate where content is inserted; remove brackets when filled.
- Nested state transitions and retry formulas are shown as pseudocode in ` ```text ` fences.
- Entity field tables show type, constraints, nullability, and usage.
- Conformance tests are itemized by area and clearly mark which tests are REQUIRED vs. OPTIONAL/Extension.

## Tailoring the Template

### Scale Down (System-to-Feature)

If the project is smaller than full system scale:

- Delete Section 8 (State Machine) if no complex state transitions exist.
- Delete Section 23 (Reference Algorithms) if no distributed/concurrent workers.
- Delete Section 24 (Conformance Matrix) and use simpler acceptance criteria instead.
- Delete Section 29–30 (Rollout, Risks) if not relevant.
- Compress Section 7 (Configuration) if config is simple.
- Fold observability into Section 20 instead of expanding monitoring interface.

### Scale Up (System-to-Extension Ecosystem)

If the project requires OPTIONAL extensions:

- Add Appendix B, C, ... for each extension profile using the Appendix A pattern.
- Create sub-tables in Section 24 showing which tests apply to each extension.
- Cross-link extension requirements from the main requirement sections using "IF [Extension] is implemented" notes.

### Multi-Document Split

If the spec grows beyond ~10,000 words:

- Keep this document as the authoritative primary contract.
- Create sub-specs for each integration surface (e.g., "Agent Runner Protocol SPEC.md") and link them by explicit reference (only permitted outside the generated spec).
- Ensure the primary spec is still readable without opening sub-specs.
