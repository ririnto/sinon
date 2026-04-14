---
title: Java API Design Reference
description: >-
  Reference for Java API design heuristics, public contracts, and review rules.
---

Use this reference when reviewing or proposing Java public contracts.

## Core Heuristics

- Prefer interfaces for capabilities and records for simple value carriers.
- Prefer explicit factory methods when invariants or naming would be unclear in overloaded constructors.
- Return interfaces such as `List` or `Map` rather than concrete collection implementations.
- Document nullability assumptions in the absence of a project-level annotation standard.
- Keep exception contracts stable and unsurprising.

## Specific Review Points

- Mutability of returned state
- Exposure of internal collections
- Equality and hash code semantics
- Serialization boundaries
- Package-private vs public visibility
- Checked vs unchecked exception choices
