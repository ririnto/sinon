---
title: Modules and Packaging Reference
description: >-
  Reference for JDK modules, packaging workflows, and related build tool decisions.
---

Use this reference when the main blocker is how to turn a known module graph into a runtime image or packaged deliverable without skipping the intermediate validation steps.

Primary references:

- `jpackage`: [Java SE 25 jpackage](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html)
- Packaging overview: [Java SE 25 packaging overview](https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html)
- `jlink`: [Java SE 25 jlink](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jlink.html)

LTS reference points for this workflow:

- JDK 8 tools overview: [Java SE 8 tools](https://docs.oracle.com/javase/8/docs/technotes/tools/)
- JDK 11 tool index: [Java SE 11 tool specifications](https://docs.oracle.com/en/java/javase/11/docs/specs/man/index.html)
- JDK 17 tool index: [Java SE 17 tool specifications](https://docs.oracle.com/en/java/javase/17/docs/specs/man/index.html)
- JDK 21 tool index: [Java SE 21 tool specifications](https://docs.oracle.com/en/java/javase/21/docs/specs/man/index.html)
- JDK 25 packaging baseline: [Java SE 25 packaging overview](https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html)

Version boundaries for this reference:

- `jdeps`: available across the supported LTS line used by this plugin
- `jlink`: JDK 9+
- `jpackage`: JDK 14+

## Practical Guidance

- Use `jdeps` first when module requirements are not yet explicit.
- Use `jlink` to shrink runtime distribution only when the module graph is stable and the target JDK actually includes `jlink`.
- Use `jpackage` when native installers or app images are part of the product requirement and the target JDK actually includes `jpackage`.
- Native packages MUST be built for their target platform; cross-platform packaging is not supported.
- On JDK 25 and later, `jpackage` no longer includes service bindings in generated runtime images by default. Add `--jlink-options --bind-services` when the application depends on service loader bindings.

## `jdeps` to `jlink` Sequence

The canonical `jdeps` and `jlink` commands are documented in the parent SKILL.md **Ready-to-Adapt Templates** section. This reference covers the sequencing logic and additive considerations when chaining them together.

Sequencing rules:

1. Run `jdeps --print-module-deps` first to enumerate required modules; treat the output as an input to `jlink`, not as a final answer by itself.
2. Feed the module list to `jlink --add-modules` only after the required modules are confirmed and the packaging goal is a trimmed runtime rather than an installer.
3. If the application relies on service loading (`ServiceLoader`), add `--bind-services` to the `jlink` command or make that requirement explicit before recommending the runtime image as complete.

This path is a JDK 9+ workflow because it depends on `jlink` and the module system.

## `jpackage` App Image or Installer Flow

The canonical `jpackage` command is documented in the parent SKILL.md **Ready-to-Adapt Templates** section. This reference covers the packaging decision logic and version-specific considerations.

Packaging rules:

1. Build an application image or installer only after the launcher inputs (main class, main jar, input directory) are already known.
2. Use `--type app-image` first to validate the packaged launch shape before choosing a platform-specific installer type (e.g., `deb`, `rpm`, `msi`, `pkg`).
3. Native packaging output is target-platform specific; produce it on the operating system that matches the final deliverable.

This path is a JDK 14+ workflow because it depends on `jpackage`.

On JDK 25 and later, add `--jlink-options --bind-services` when the packaged application depends on service bindings that earlier `jpackage` defaults used to include automatically. The full form preserves the default stripping behavior while re-adding service bindings:

```bash
jpackage [...] --jlink-options --strip-native-commands --strip-debug \
               --no-man-pages --no-header-files --bind-services
```
