---
title: "Domain Ingest Contract"
description: "Behavioral contract for the domain ingest capability"
last_updated: "2026-03-06"
metadata: {}
---

## Contract Scope

This document defines the contract unit for the domain ingest capability.
The contract focuses on request validation, persistence guarantees, and stable outcomes.

## Contract Units

### Unit: domain_ingest_service

- Kind: `module`
- Location: `src/domain/ingest`
- Responsibility: Applies ingest validation, persistence, and outcome mapping.
- Covers requirement(s): `Request Validation`, `Accepted Request Processing`
- Covers scenario(s): `Normal Flow`, `Alternative Flow`, `Error Flow`

#### Input Rules (domain_ingest_service)

| Item | Type | Required | Rules |
| --- | --- | --- | --- |
| requestId | string | yes | Must be non-empty and unique per request. |
| note | string | no | May be omitted; when present, it is stored as received. |

#### Output Rules (domain_ingest_service)

| Item | Type | Guarantee |
| --- | --- | --- |
| outcome | string | Returns `accepted` or `invalid_request`. |
| recordId | string | Present only when persistence succeeds. |

#### Failure Modes (domain_ingest_service)

| Condition | Outcome |
| --- | --- |
| Missing requestId | Returns `invalid_request` and writes no state. |
| Persistence failure | Returns a documented error and exposes no partial |
| | write. |

#### Behavioral Constraints (domain_ingest_service)

- Invariant: Persisted records always include the accepted request identifier.
- Edge cases: Empty note values are accepted and stored consistently.
- Side effects: Writes exactly one record for each accepted request.
- Idempotency: Equivalent retries with the same accepted request preserve the same stored meaning.
- Concurrency: Concurrent accepted requests preserve record consistency.
- Async semantics: Success is visible only after persistence is committed.

#### Scenario Mapping (domain_ingest_service)

| Scenario | Related requirement | Unit behavior | Expected result |
| --- | --- | --- | --- |
| Normal Flow | Accepted Request Processing | Validates and persists the | |
| | | request. | Returns `accepted`. |
| Alternative Flow | Accepted Request | Persists the optional note | |
| | Processing | with the request. | Returns `accepted`. |
| Error Flow | Request Validation | Rejects the invalid request before | |
| | | persistence. | Returns `invalid_request`. |

## Examples by Unit and Scenario

| Example ID | Unit | Scenario | Purpose |
| --- | --- | --- | --- |
| EX-INGEST-001 | domain_ingest_service | Normal Flow | |
| | | | Demonstrates the primary accepted request payload. |

### EX-INGEST-001: domain_ingest_service / Normal Flow

```yaml
input:
  requestId: "ingest-request-001"
  note: "primary path"
output:
  outcome: "accepted"
  recordId: "record-001"
```
