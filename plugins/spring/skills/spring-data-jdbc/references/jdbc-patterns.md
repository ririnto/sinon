---
title: Spring Data JDBC Patterns Reference
description: >-
  Reference for Spring Data JDBC aggregate boundaries, mapping checks, and usage patterns.
---

Use this reference when the main blocker is how to keep aggregate ownership clear once the basic root-and-repository shape is already settled.

## Aggregate Ownership Heuristic

Keep one aggregate root responsible for child rows that do not have an independent lifecycle. A child collection should usually stay inside the aggregate when:

- the parent and child are loaded and saved together
- the child rows are not queried as a top-level concept
- deleting the parent should remove the child rows as part of the same ownership boundary

A typical Spring Data JDBC shape uses an aggregate root with an owned collection mapped by `@MappedCollection(idColumn = "order_id")`. Keep that ownership explicit instead of modeling the child rows like separate JPA entities.

## Mapping Checklist

- ID handling is explicit
- aggregate ownership is obvious
- repository contract reads like aggregate operations
- child rows are owned because they change with the aggregate, not because they merely share a table neighborhood

## Repository Versus Lower-Level JDBC Rule

Stay with the repository when the operation still reads like loading or saving the whole aggregate. Drop to lower-level JDBC only when the query is reporting-oriented, joins across unrelated aggregates, or needs a shape the aggregate model should not own.

## Child Collection Update Note

Spring Data JDBC persists the aggregate as a whole. When a child collection changes, reason about the full owned collection state instead of expecting JPA-style dirty checking on individual child entities.

## Common Mistakes

- expecting lazy-loading or complex ORM behavior
- designing repositories around tables instead of aggregates
- splitting child rows into their own repository too early when the lifecycle is still aggregate-owned
