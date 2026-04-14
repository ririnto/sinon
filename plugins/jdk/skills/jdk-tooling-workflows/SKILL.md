---
name: jdk-tooling-workflows
description: >-
  This skill should be used when the user asks to "use javac", "package a Java app", "compare jlink and jpackage", "generate Javadoc", "analyze dependencies with jdeps", or needs guidance on official JDK toolchain workflows.
---

# JDK Tooling Workflows

## Overview

Use this skill to guide tasks that depend on official JDK command-line tools and packaging flows. The common case is choosing the smallest standard tool sequence that produces a real deliverable without hiding behind build wrappers. Prefer direct JDK commands first, then layer repository-specific build glue on top only if needed.

## Use This Skill When

- You need to compile, run, document, inspect, slim, or package a Java application with standard JDK tools.
- You need to decide between `jdeps`, `jlink`, and `jpackage`.
- You need a direct command sequence before mapping it into Maven, Gradle, or another wrapper.
- Do not use this skill when the task starts from a live JVM symptom and the next step is runtime evidence collection rather than build or packaging flow.

## Common-Case Workflow

1. Read the target outcome first: compile, run, dependency analysis, documentation, runtime image, or installer.
2. Choose the smallest official JDK tool that directly moves that outcome forward.
3. Use `jdeps` before `jlink` when module boundaries or runtime dependencies are not yet explicit.
4. Use `jpackage` only after the launcher/runtime image strategy is already stable.

## Minimal Setup

Confirm the local JDK toolchain first:

```bash
java --version
javac --version
```

For module-aware slimming or packaging, also confirm the input artifact you will analyze or package exists before invoking `jdeps`, `jlink`, or `jpackage`.

## First Runnable Commands or Code Shape

Compile and run a single class directly:

```bash
javac -d out src/main/java/com/example/demo/App.java
java -cp out com.example.demo.App
```
Use when: you need the smallest direct JDK path before bringing in Maven or Gradle.

## Ready-to-Adapt Templates

Dependency and module inspection with `jdeps`:

```bash
jdeps --multi-release 21 --print-module-deps app.jar
```
Use when: you want to know whether `jlink` is even justified and which modules matter.

Custom runtime image with `jlink`:

```bash
jlink \
  --add-modules java.base,java.net.http \
  --output build/runtime
```
Use when: the module graph is already known and the deliverable is a trimmed runtime.

Native packaging with `jpackage`:

```bash
jpackage \
  --name DemoApp \
  --input build/libs \
  --main-jar demo-app.jar \
  --main-class com.example.demo.App
```
Use when: the product requirement includes an installable or native-looking distribution.

Javadoc generation:

```bash
javadoc -d build/docs src/main/java/com/example/demo/*.java
```
Use when: the deliverable is documentation rather than packaging or runtime analysis.

## Validate the Result

Use the smallest validation that matches the chosen tool:

```bash
java --list-modules | grep java.base
```

- After `javac`, confirm the class output exists and `java -cp` starts successfully.
- After `jdeps`, confirm the reported modules match the application expectations.
- After `jlink`, run `build/runtime/bin/java --version` to verify the image is usable.
- After `jpackage`, verify the generated app image or installer exists for the target platform.

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| choosing among the wider JDK tool set before narrowing the command sequence | `./references/jdk-tool-index.md` |
| module-aware slimming or native packaging details | `./references/modules-packaging.md` |

## Invariants

- MUST prefer official JDK tools before extra wrappers.
- MUST verify Java version assumptions before using newer tool features.
- MUST explain prerequisites such as modules, runtime images, or platform packaging limits.
- SHOULD show the actual sequence of tools rather than isolated commands.
- SHOULD use `jdeps` before `jlink` when module boundaries are unclear.
- MUST use `jpackage` only after the launcher and runtime-image strategy is settled.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| starting with `jpackage` | packaging too early hides runtime and module problems | inspect with `jdeps` first, then build the runtime/image chain |
| using `jlink` without explicit module knowledge | the image can miss required modules or stay larger than necessary | run `jdeps --print-module-deps` first |
| treating build wrappers as the source of truth | you lose the standard-tool sequence underneath | describe the raw JDK command flow first |
| ignoring target-platform packaging limits | native packages are platform-specific | call out platform scope before recommending `jpackage` |

## Scope Boundaries

- Activate this skill for:
  - standard JDK compile, run, inspect, document, and packaging workflows
  - choosing among `javac`, `java`, `jdeps`, `jlink`, and `jpackage`
  - explaining direct JDK command sequences behind wrappers
- Do not use this skill as the primary source for:
  - runtime diagnostics starting from live JVM symptoms
  - GC evidence and collector choice
  - Java language design or test-structure guidance
