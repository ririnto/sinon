# Requirements Style

Use this guide when writing or reviewing requirements inside `SPEC.md`.

## Requirement IDs

Use stable IDs that remain valid even if requirements are reordered.

| Prefix | Use |
| --- | --- |
| `FR-001` | Functional requirement |
| `NFR-PERF-001` | Performance requirement |
| `NFR-SEC-001` | Security or privacy requirement |
| `NFR-REL-001` | Reliability or availability requirement |
| `NFR-OBS-001` | Observability requirement |
| `NFR-UX-001` | Usability or accessibility requirement |
| `AC-001-A` | Acceptance criterion tied to requirement 001 |
| `SC-001` | Success criterion |

Do not reuse IDs after deleting or replacing a requirement. Mark removed requirements in a change log or delta spec when historical traceability matters.

## Normative Language

Use RFC 2119 language deliberately.

| Keyword | Meaning |
| --- | --- |
| `MUST` or `SHALL` | Absolute requirement |
| `MUST NOT` or `SHALL NOT` | Absolute prohibition |
| `SHOULD` | Recommended unless a justified exception exists |
| `SHOULD NOT` | Discouraged unless a justified exception exists |
| `MAY` | Optional behavior |

Prefer `MUST` for product and feature requirements. Use `SHALL` when matching formal SRS, protocol, or compliance style.

## EARS Patterns

Use EARS-style sentence patterns to reduce ambiguity.

| Pattern | Form | Use |
| --- | --- | --- |
| Ubiquitous | The system SHALL [behavior]. | Always-active behavior |
| Event-driven | WHEN [trigger], the system SHALL [response]. | Behavior caused by an event |
| State-driven | WHILE [state], the system SHALL [behavior]. | Behavior active during a state |
| Unwanted behavior | IF [condition], THEN the system SHALL [response]. | Error, abuse, or failure handling |
| Optional feature | WHERE [feature is enabled], the system SHALL [behavior]. | Feature-flagged or conditional behavior |

## Good Requirement Shape

Write one observable behavior per requirement.

```markdown
- FR-001: WHEN a signed-in user submits a valid export request, the system MUST create an export job and return a tracking identifier.
- FR-002: IF an export request exceeds the allowed date range, THEN the system MUST reject the request with an explanation of the allowed range.
- NFR-PERF-001: The system MUST create an export job within 2 seconds for 95 percent of valid requests measured at the service boundary.
```

## Poor Requirement Shape

Avoid requirements that are vague, compound, or implementation-led.

```markdown
- FR-001: The system should handle exports well.
- FR-002: Use Redis and a worker queue to process exports.
- FR-003: The export feature must be fast, secure, intuitive, and scalable.
```

## Requirement Rules

- A requirement MUST be complete enough to test or inspect.
- A requirement MUST NOT contain multiple unrelated behaviors joined by `and`.
- A requirement MUST NOT specify implementation details unless the implementation is a real constraint.
- A requirement SHOULD name limits, thresholds, units, states, and transitions when they affect behavior.
- A functional requirement MUST link to at least one scenario or state why it cannot be scenario-tested.
- A non-functional requirement SHOULD include a metric, measurement point, and verification method.
- Unknowns MUST be written as open questions or `[NEEDS CLARIFICATION: specific question]`, not hidden as assumptions.

## Delta Requirements

For updates to an existing spec, organize changes as:

```markdown
## Added Requirements

- FR-004: The system MUST [new behavior].

## Modified Requirements

- FR-002: [Full updated requirement text].
  - Previously: [old text].
  - Reason: [why it changed].

## Removed Requirements

- FR-003: [removed requirement title].
  - Reason: [why removed].
  - Migration: [what replaces it, if anything].
```

Use delta sections when review clarity matters. Fold deltas into the canonical spec after approval when the spec should remain a clean source of truth.
