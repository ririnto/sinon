---
title: Spring Data JDBC Patterns Reference
description: >-
  Reference for Spring Data JDBC aggregate boundaries, mapping checks, and usage patterns.
---

Use this reference when deciding how to map an aggregate in Spring Data JDBC without drifting into JPA expectations.

## Mapping Checklist

- ID handling is explicit
- aggregate ownership is obvious
- repository contract reads like aggregate operations

## Common Mistakes

- expecting lazy-loading or complex ORM behavior
- designing repositories around tables instead of aggregates
