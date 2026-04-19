# Spring Batch integration-driven launch

Open this reference when the task involves launching jobs through integration channels or feedback messaging instead of direct application calls.

## Integration blocker

Use integration-driven launch patterns when jobs start from messages, schedules, or externalized orchestration rather than direct application calls.

- launch from a message only when job parameters and idempotency rules are explicit
- publish job completion or failure feedback when downstream systems depend on status
- keep launch transport concerns out of the batch step implementation itself

## Decision points

| Situation | First check |
| --- | --- |
| Jobs start from external events | make parameter identity and idempotency explicit |
| Downstream systems depend on completion state | emit explicit success or failure feedback |

## Pitfalls

- Do not let message transport shape leak into reader, processor, or writer logic.
- Do not launch from integration channels before parameter identity and rerun semantics are settled.
