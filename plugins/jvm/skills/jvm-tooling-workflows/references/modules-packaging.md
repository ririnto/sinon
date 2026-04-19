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
- Mention that native packages must be built for their target platform.
- On JDK 25 and later, call out that `jpackage` no longer includes service bindings in generated runtime images by default.

## `jdeps` to `jlink` Sequence

Start by printing the module dependencies of the application artifact and treat the output as an input to packaging, not as a final answer by itself:

```bash
jdeps --multi-release 21 --print-module-deps app.jar
```

Then build the runtime image only after the required modules are explicit and the packaging goal is still a trimmed runtime rather than an installer:

```bash
jlink \
  --add-modules java.base,java.net.http \
  --output build/runtime
```

Use this path when the goal is a smaller runtime image rather than an installer.

This path is a JDK 9+ workflow because it depends on `jlink` and the module system.

If the application relies on service loading, add `--bind-services` to the `jlink` flow or make that requirement explicit before recommending the runtime image as complete.

## `jpackage` App Image or Installer Flow

Build an application image or installer only after the launcher inputs are already known and a plain runtime image is not enough for the product requirement:

```bash
jpackage \
  --name DemoApp \
  --input build/libs \
  --main-jar demo-app.jar \
  --main-class com.example.demo.App \
  --type app-image
```

Use `app-image` first when you want to validate the packaged launch shape before choosing a platform-specific installer type. Native packaging output is target-platform specific, so produce it on the operating system that matches the final deliverable.

This path is a JDK 14+ workflow because it depends on `jpackage`.

On JDK 25 and later, add `--jlink-options --bind-services` when the packaged application depends on service bindings that earlier `jpackage` defaults used to include automatically.
