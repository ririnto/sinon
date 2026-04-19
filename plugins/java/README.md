---
title: Java
description: >-
  Overview of the Java plugin, its included skills, runtime model, and Java LSP setup guidance.
---

Java is a shared, skill-first plugin for Java language work in the Sinon universal marketplace.

## Purpose

- Provide reusable Java workflows that remain portable across Claude Code and Codex-style plugin systems.
- Keep the portable value surface in `skills/`, with Java-specific language-server integration kept separate from the shared skill corpus.
- Keep skills practical, example-driven, and focused on direct Java implementation work rather than router-style guidance.
- Ground guidance in official Java, JUnit, Gradle, Maven, and JDT LS references instead of ad-hoc advice.

## Included Skills

- `java-language-syntax`: Java grammar, expression forms, LTS-boundary syntax differences, and foundational `java.base` coverage that materially affects code-shape guidance.
- `java-language-design`: language features, API design, exceptions, immutability, and collections guidance.
- `java-test`: JUnit 5, red-green-refactor, mocking boundaries, and Java test execution guidance.
- `java-performance-concurrency`: virtual threads, profiling, contention analysis, and performance review.
- `java-dependency-versioning`: Maven Central coordinate and current-release lookup guidance.

These skills are meant to help complete Java work directly inside the current repository. They should not stop at pointing toward other repositories or documentation when the local task can already be unblocked with stable Java guidance.

## When to Use Which Skill

Start here when the question is still fuzzy:

- If the question is "can this Java baseline compile or express it?", stay in syntax-oriented guidance; if the question is "which foundational Java SE API family in `java.base` should I reach for while staying within a given LTS baseline?", still start in syntax-oriented guidance; if the question is "should this type or API be modeled this way?", stay in design-oriented guidance.
- Java baseline, grammar, syntax compatibility, and foundational `java.base` standard-library coverage questions belong in the syntax-oriented guidance.
- Type modeling, API shape, immutability, and exception-boundary questions belong in the design-oriented guidance.
- JUnit structure, red-green-refactor sequencing, and test-execution setup belong in the testing guidance.
- Virtual-thread fit, contention, and profiling-driven bottleneck review belong in the performance and concurrency guidance.
- Maven coordinate lookup and current dependency-release checks belong in the dependency guidance.

Typical workflow:

1. Confirm what the current Java LTS baseline allows before changing syntax or recommending newer foundational APIs.
2. Shape the API or type model before broad refactors.
3. Lock behavior with tests before or while changing implementation.
4. Review concurrency and performance only after there is real evidence of a bottleneck.
5. Check dependency coordinates and current releases before hardcoding version text.

## Runtime Model

This plugin uses one shared plugin root with two thin runtime manifests:

- `.claude-plugin/plugin.json`
- `.codex-plugin/plugin.json`

The actual reusable content lives beside those manifests at the plugin root.

## Claude-Specific Surfaces

Claude Code gets Java language-server support through `.lsp.json` and `scripts/jdtls-wrapper.sh`.

- The wrapper expects `jdtls` to be installed on `PATH`.
- Use JDK 21 or newer for the `jdtls` runtime baseline.
- Built-in Lombok selection happens at startup, but this plugin does not vendor a fallback Lombok jar.

## Design Principles

- Prefer one job per skill.
- Keep `SKILL.md` concise and procedural.
- Keep skill guidance directly actionable, with examples or decision rules that unblock implementation in the current task.
- Move dense material into `references/`.
- Unnecessary blank lines inside function bodies SHOULD be removed.
- Variables used only once SHOULD be inlined when their names and extraction order do not add meaning.
- For Java declaration ordering, treat Kotlin-style reading order as an optional codebase convention rather than a Java language rule.
- Avoid runtime-specific fragmentation unless a platform truly requires it.
- Keep manifests thin and let marketplace catalogs describe distribution.

## Installation

Install from Sinon:

```bash
/plugin install java@sinon
```

For local development:

```bash
cc --plugin-dir /path/to/sinon/plugins/java
```

## Scope Notes

This plugin intentionally focuses on shared, implementation-oriented skill coverage. It does not yet ship:

- custom MCP servers
- hooks
- framework-specific Spring or Jakarta EE specializations

## Java LSP Setup

The plugin ships a Claude-compatible `.lsp.json` entry for `jdtls`.

Use JDTLS when the task needs Java symbol navigation, diagnostics, or refactors. Do not treat it as a substitute for the skills above: the skills explain how to reason about Java work, while JDTLS provides editor intelligence for `.java` files.

### Requirements

- `jdtls` executable available on `PATH`
- JDK 21 or newer available for the `jdtls` runtime

## Java 25 and LTS Framing

- Treat Java version-difference guidance in this plugin as LTS-first: `8`, `11`, `17`, `21`, and `25`.
- Treat `java.base` coverage here as foundational Java SE standard-library guidance rather than as a claim about broader `jdk.*` tooling or diagnostics.
- Use the Java plugin for code-shape questions tied to core packages such as `java.lang`, `java.util`, `java.time`, `java.io`, `java.nio`, `java.net`, and related `java.base` SPI families.
- `jdeps`, `jlink`, `jpackage`, runtime images, packaging chains, and live JVM diagnostics are outside this plugin's scope.

### Lombok Source Selection

The wrapper now selects a Lombok source at startup in this order:

1. Explicit override jar from `JAVA_ASSISTANT_LOMBOK_JAR`, `JDK_ASSISTANT_LOMBOK_JAR` (legacy alias), or `LOMBOK_JAR`
2. Compatible project jar discovered from `.classpath` or `.factorypath`

This is intentionally closer to the VS Code Java extension in one specific way: startup chooses the effective Lombok jar and prefers a compatible project jar when it can resolve one. Unlike VS Code Java, this plugin does not ship its own fallback Lombok jar.

> [!WARNING]
> Project-discovered Lombok jars are **trusted executable code** loaded as a `-javaagent`. Only use this auto-discovery behavior in trusted repositories and workspaces. In untrusted environments, set `JAVA_ASSISTANT_LOMBOK_ENABLED=false` to disable it entirely. When Lombok support is needed in an untrusted context, prefer an explicit trusted override jar via one of the environment variables below.

To provide an explicit override jar, point one of these environment variables at a local `lombok.jar`:

- `JAVA_ASSISTANT_LOMBOK_JAR` (preferred)
- `JDK_ASSISTANT_LOMBOK_JAR` (legacy compatibility alias)
- `LOMBOK_JAR`

The wrapper injects the selected jar through `JDK_JAVA_OPTIONS=-javaagent:...`.

Project detection signals still matter, but they are now used to prefer a project-local jar when it can be resolved:

- `pom.xml` with `org.projectlombok` or `lombok`
- `build.gradle` or `build.gradle.kts` with Lombok coordinates, Lombok plugins, or common Lombok dependency configurations
- `gradle/libs.versions.toml` with Lombok aliases or coordinates
- `.classpath` or `.factorypath` entries mentioning Lombok

The wrapper first checks the discovered project root and then scans nested Maven, Gradle, and Eclipse metadata files under that root while skipping common build output directories.

If no override is supplied and no compatible project jar can be resolved, the wrapper starts plain `jdtls` without Lombok support. Set `JAVA_ASSISTANT_LOMBOK_ENABLED=false` to disable Lombok selection entirely. `JDK_ASSISTANT_LOMBOK_ENABLED` is a legacy compatibility alias for the same setting.

For safety, override jar paths must be single filesystem tokens without whitespace.
