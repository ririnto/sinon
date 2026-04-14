---
title: Spring Kafka Patterns Reference
description: >-
  Additive patterns for Spring Kafka: concurrency rules, transaction boundaries, consumer consistency, and commit visibility.
---

Use this reference for delivery semantics and topology depth beyond the basic producer/listener shape.

## Concurrency Rule

Increase listener concurrency only when:

- partitioning supports it
- handlers are safe to run in parallel
- ordering assumptions are still acceptable

## Transaction Boundary Rule

Do not assume one database write and one Kafka publish are atomic just because both appear in one Spring service method.

- if one business write and one downstream message must stay consistent, prefer a transactional outbox design
- keep idempotent consumer behavior explicit because replay and duplicate delivery are normal failure modes

State the outbox implementation and dual-write remediation pattern directly in the active skill context instead of sending readers to another skill's references.

## Consumer Consistency Rule

Make duplicate tolerance explicit. Because Kafka replay and at-least-once delivery are normal failure modes, consumers must tolerate duplicates. Keep any idempotent-consumer code pattern and commentary with the current skill material instead of linking across skills.

## Commit Visibility Rule

When transactional producers or `read_committed` consumers matter, state that requirement explicitly instead of assuming default semantics are enough.

Transactional producer nuance, consumer isolation, and retry-topic caveats should stay with the active Kafka configuration guidance.

Transactional outbox, polling relay, and idempotent-consumer guidance belongs with the active messaging or transaction documentation rather than a different skill's references.
