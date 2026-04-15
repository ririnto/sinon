---
title: Modules and Packaging Reference
description: >-
  Reference for JDK modules, packaging workflows, and related build tool decisions.
---

Use this reference when the main blocker is how to turn a known module graph into a runtime image or packaged deliverable without skipping the intermediate validation steps.

Primary references:

- `jpackage`: <https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html>
- Packaging overview: <https://docs.oracle.com/en/java/javase/26/jpackage/packaging-overview.html>
- `jlink`: <https://docs.oracle.com/en/java/javase/26/docs/specs/man/jlink.html>

## Practical Guidance

- Use `jdeps` first when module requirements are not yet explicit.
- Use `jlink` to shrink runtime distribution only when the module graph is stable.
- Use `jpackage` when native installers or app images are part of the product requirement.
- Mention that native packages must be built for their target platform.

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
