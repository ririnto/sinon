---
title: Java
description: >-
  Overview of the Java plugin, its included skills, runtime model,
  orchestration routing, and Java LSP setup guidance.
---

Java is a shared, skill-first plugin for Java language work in the Sinon universal marketplace.

## Purpose

- Provide reusable Java workflows that remain portable across Claude Code and Codex-style plugin systems.
- Keep the portable value surface in `skills/`, with Java-specific language-server integration kept separate from the shared skill corpus.
- Keep skills practical, example-driven, and focused on direct Java implementation work rather than router-style guidance.
- Ground guidance in official Java, JUnit, Gradle, Maven, and JDT LS references instead of ad-hoc advice.

## Included Skills

| Skill | Job | Trigger |
| --- | --- | --- |
| `java-language-syntax` | Java grammar, LTS-boundary syntax differences, foundational `java.base` coverage | "explain Java syntax", "rewrite for Java X", "is this valid on Java 17" |
| `java-language-design` | API shape, type modeling, immutability, exception contracts, collection exposure | "design a Java API", "review class structure", "records vs sealed classes" |
| `java-test` | JUnit 5 TDD, Mockito boundaries, Awaitility async, build-tool test wiring | "write a JUnit test", "follow TDD in Java", "fix failing test" |
| `java-performance-concurrency` | Profiling strategy, virtual-thread fit, contention analysis, bottleneck classification | "optimize Java performance", "use virtual threads", "profile Java code" |
| `java-dependency-versioning` | Maven Central coordinate lookup, current-release verification, install snippets | "find latest version", "look up artifact coordinate", "check Maven Central" |

These skills are meant to help complete Java work directly inside the current repository. They should not stop at pointing toward other repositories or documentation when the local task can already be unblocked with stable Java guidance.

## Orchestration Routing

Start here when the question is still fuzzy:

### Syntax vs design vs test vs performance vs dependency

- If the question is "can this Java baseline compile or express it?", route to **syntax**.
- If the question is "which foundational `java.base` API family should I reach for?", route to **syntax** (`java-base-family-map` reference).
- If the question is "should this type or API be modeled this way?", route to **design**.
- If the question is about type modeling, immutability, exception boundaries, or collection exposure, route to **design**.
- If the question is about JUnit structure, red-green-refactor sequencing, or test execution setup, route to **test**.
- If the question is about virtual-thread fit, contention, profiling-driven bottleneck review, route to **performance**.
- If the question is about Maven coordinate lookup or current dependency-release checks, route to **dependency**.

### Typical workflow

1. Confirm what the current Java LTS baseline allows before changing syntax or recommending newer foundational APIs (**syntax**).
2. Shape the API or type model before broad refactors (**design**).
3. Lock behavior with tests before or while changing implementation (**test**).
4. Review concurrency and performance only after there is real evidence of a bottleneck (**performance**).
5. Check dependency coordinates and current releases before hardcoding version text (**dependency**).

### Cross-skill scope boundaries

Each skill explicitly states what it does not cover and which sibling skill handles that territory instead. When a question lands on a boundary, follow the scope-boundaries section in the active skill's SKILL.md.

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

The wrapper selects a Lombok source at startup in this order:

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

Project detection signals used to prefer a project-local jar when it can be resolved:

- `pom.xml` with `org.projectlombok` or `lombok`
- `build.gradle` or `build.gradle.kts` with Lombok coordinates, Lombok plugins, or common Lombok dependency configurations
- `gradle/libs.versions.toml` with Lombok aliases or coordinates
- `.classpath` or `.factorypath` entries mentioning Lombok

The wrapper first checks the discovered project root and then scans nested Maven, Gradle, and Eclipse metadata files under that root while skipping common build output directories.

If no override is supplied and no compatible project jar can be resolved, the wrapper starts plain `jdtls` without Lombok support. Set `JAVA_ASSISTANT_LOMBOK_ENABLED=false` to disable Lombok selection entirely. `JDK_ASSISTANT_LOMBOK_ENABLED` is a legacy compatibility alias for the same setting.

For safety, override jar paths must be single filesystem tokens without whitespace.
