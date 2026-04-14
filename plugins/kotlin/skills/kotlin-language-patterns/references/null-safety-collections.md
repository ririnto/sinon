---
title: Null Safety and Collections
description: >-
  Reference for readable Kotlin null handling and collection-pipeline decisions without overusing scope functions.
---

Use this reference when Kotlin code becomes harder to read because null-handling or collection chains are doing too much work at once.

Null-safety rules:

- prefer nullable types plus explicit handling over `!!`
- use early returns when absence is exceptional to the current path
- use scope functions only when they make ownership or transformation clearer

Collection guidance:

- use `map`, `filter`, and `associate` when the pipeline is still easy to read in one pass
- break long chains into named locals when business meaning is getting hidden
- use sequence-based processing only when laziness materially helps, not by default

Prefer a named local over nested scope functions when the chain stops reading like direct business logic.
