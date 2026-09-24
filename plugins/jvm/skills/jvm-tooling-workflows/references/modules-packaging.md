---
description: >-
  Reference for JDK modules, packaging workflows, and related build tool decisions.
---

# Modules and Packaging Reference

Use this reference when the main blocker is how to turn a known module graph into a runtime image or packaged deliverable without skipping the intermediate validation steps.

Version boundaries for this reference:

- `jdeps`: available across the supported LTS line used by this plugin.
- `jlink`: JDK 9+ (part of the module system).
- `jpackage`: incubating in JDK 14-15 (`jdk.incubator.jpackage`), standard tool from JDK 16 onward.
  - Do not treat the incubator form on JDK 14-15 as production-grade, and do not present `jpackage` as available on JDK 8 or JDK 11.

## Practical Guidance

- Use `jdeps` first when module requirements are not yet explicit.
- Use `jlink` to shrink runtime distribution only when the module graph is stable and the target JDK actually includes `jlink`.
- Use `jpackage` when native installers or app images are part of the product requirement and the target JDK actually ships the standard tool (JDK 16+).
- Native packages MUST be built for their target platform.
  - Cross-platform packaging is not supported.
- On JDK 25 and later, `jpackage` no longer includes `--bind-services` in its default `jlink` options.
  - When the packaged application relies on `java.util.ServiceLoader`, pass a single quoted `--jlink-options` string that restores the strip defaults and re-enables service binding.
  - See the `jpackage` section below for the full form.

## `jdeps` to `jlink` Sequence

Use this runnable sequence when you need to derive a module list and build a trimmed runtime image:

```sh
jdeps --multi-release 21 --print-module-deps app.jar
jlink \
    --add-modules java.base,java.net.http \
    --output build/runtime
build/runtime/bin/java --version
```

Sequencing rules:

1. Run `jdeps --print-module-deps` first to enumerate required modules.
   - Treat the output as an input to `jlink`, not as a final answer by itself.
2. Feed the module list to `jlink --add-modules` only after the required modules are confirmed and the packaging goal is a trimmed runtime rather than an installer.
3. If the application relies on service loading (`ServiceLoader`), add `--bind-services` to the `jlink` command or make that requirement explicit before recommending the runtime image as complete.

This path is a JDK 9+ workflow because it depends on `jlink` and the module system.

## `jpackage` App Image or Installer Flow

Use this runnable shape when launcher inputs are already known and the goal is an app image before a platform-specific installer:

```sh
jpackage \
    --name DemoApp \
    --input build/libs \
    --main-jar demo-app.jar \
    --main-class com.example.demo.App \
    --type app-image
```

Packaging rules:

1. Build an application image or installer only after the launcher inputs (main class, main jar, input directory) are already known.
2. Use `--type app-image` first to validate the packaged launch shape before choosing a platform-specific installer type (e.g., `deb`, `rpm`, `msi`, `pkg`).
3. Native packaging output is target-platform specific.
   - Produce it on the operating system that matches the final deliverable.

This path is a JDK 16+ workflow because `jpackage` is a standard tool only from JDK 16.
On JDK 14-15 the tool is an incubator (`jdk.incubator.jpackage`) and its command name and options MAY differ from the standard form.

On JDK 25 and later, restore service binding with a quoted, space-separated `--jlink-options` argument.
Each `--jlink-options` occurrence takes one space-separated string, and the option may be used multiple times.
Do not pass jlink flags as standalone `jpackage` arguments.
When you pass `--jlink-options`, it replaces the jpackage default list entirely, so include the four strip flags explicitly if a lean runtime image is still the goal:

```sh
jpackage [...] \
    --jlink-options "--strip-native-commands --strip-debug --no-man-pages --no-header-files --bind-services"
```

When the only goal is to restore service binding without asserting the strip defaults yourself, pass only that flag:

```sh
jpackage [...] --jlink-options "--bind-services"
```
