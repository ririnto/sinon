---
title: Modules and Packaging Reference
description: >-
  Reference for JDK modules, packaging workflows, and related build tool decisions.
---

Primary references:

- `jpackage`: <https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html>
- Packaging overview: <https://docs.oracle.com/en/java/javase/26/jpackage/packaging-overview.html>
- `jlink`: <https://docs.oracle.com/en/java/javase/26/docs/specs/man/jlink.html>

## Practical Guidance

- Use `jdeps` first when module requirements are not yet explicit.
- Use `jlink` to shrink runtime distribution only when the module graph is stable.
- Use `jpackage` when native installers or app images are part of the product requirement.
- Mention that native packages must be built for their target platform.
