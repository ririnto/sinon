---
title: Java Language Features Reference
description: >-
  Reference for Java language feature behavior and version-sensitive feature lookup.
---

Use these official and high-signal references when the Java baseline is already known and the remaining blocker is language-feature tradeoff rather than syntax availability lookup.

- Oracle Java SE documentation hub: <https://docs.oracle.com/en/java/>
- Oracle Java API documentation index: <https://docs.oracle.com/en/java/javase/index.html>
- OpenJDK JEP index: <https://openjdk.org/jeps/0>

## Review Questions

- Does a record model value semantics more clearly than a mutable class here?
- Is a sealed hierarchy genuinely closed inside the module, or is future extension still expected?
- Would a preview-only construct make the public API or operational baseline harder to support?

## Guidance

- Mention tradeoffs between older class hierarchies and newer record or sealed-type models.
- Prefer public API choices that remain understandable on the repository's actual Java baseline.
- Treat preview features as an explicit product decision, not a default design move.
