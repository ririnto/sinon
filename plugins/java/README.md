---
title: Java
description: >-
  Overview of the Java plugin, its included skills, runtime model,
  skill selection guidance, and Java LSP setup guidance.
---

Java is a shared, skill-first plugin for Java language work in the Sinon Claude marketplace.

## Purpose

- Provide reusable Java workflows that remain portable across Claude Code plugin installations.
- Keep the portable value surface in `skills/`, with Java-specific language-server integration kept separate from the shared skill corpus.
- Keep skills practical, example-driven, and focused on direct Java implementation work rather than directory-style guidance.
- Ground guidance in plugin bundled skills, wrapper behavior, and stable Java ecosystem conventions instead of ad-hoc advice.

## Included Skills

| Skill | Job | Trigger |
| --- | --- | --- |
| `java-language-syntax` | Java grammar, LTS-boundary syntax differences, foundational `java.base` coverage | "explain Java syntax", "rewrite for Java X", "is this valid on Java 17" |
| `java-language-design` | API shape, type modeling, immutability, exception contracts, collection exposure | "design a Java API", "review class structure", "records vs sealed classes" |
| `java-test` | JUnit 5 TDD, Mockito boundaries, Awaitility async, build-tool test wiring | "write a JUnit test", "follow TDD in Java", "fix failing test" |
| `java-performance-concurrency` | Profiling strategy, virtual-thread fit, contention analysis, bottleneck classification | "optimize Java performance", "use virtual threads", "profile Java code" |
| `java-dependency-versioning` | Maven Central coordinate lookup, release-verification path, install snippets; live registry access is required for current-release confirmation | "find latest version", "look up artifact coordinate", "check Maven Central" |

These skills are meant to help complete Java work directly inside the current repository. They should not stop at pointing toward other repositories or documentation when the local task can already be unblocked with stable Java guidance.

## Included Agents

- `java-architect`: coordinates Java language, testing, dependency, performance, and API design decisions when a task crosses multiple Java skills or needs architecture-level tradeoff review.

## Included Commands

This plugin ships no commands.

## Skill Selection

Start here when the Java work could fit more than one skill:

### Syntax vs design vs test vs performance vs dependency

- Use `java-language-syntax` when the question is "can this Java baseline compile or express it?".
- Use `java-language-syntax` when the question is "which foundational `java.base` API family should I reach for?".
- Use `java-language-design` when the question is "should this type or API be modeled this way?".
- Use `java-language-design` for type modeling, immutability, exception boundaries, or collection exposure.
- Use `java-test` for JUnit structure, red-green-refactor sequencing, or test execution setup.
- Use `java-performance-concurrency` for virtual-thread fit, contention, profiling-driven bottleneck review.
- Use `java-dependency-versioning` for Maven coordinate lookup or current dependency-release checks.
- Use `java-architect` when the task crosses several Java skill areas or needs an architecture-level decision before implementation.

### Typical workflow

1. Confirm what the current Java LTS baseline allows before changing syntax or recommending newer foundational APIs with `java-language-syntax`.
2. Shape the API or type model before broad refactors with `java-language-design`.
3. Lock behavior with tests before or while changing implementation with `java-test`.
4. Review concurrency and performance only after there is real evidence of a bottleneck with `java-performance-concurrency`.
5. Check dependency coordinates and current releases before hardcoding version text with `java-dependency-versioning`; current-release confirmation requires live registry access.

### Scope boundaries

Each skill states its own scope and exclusions. When work lands on a boundary, keep the active task inside the skill that owns the current implementation blocker instead of turning the answer into transfer instructions.

## Runtime Model

This plugin uses one shared plugin root with a thin Claude manifest:

- `.claude-plugin/plugin.json`

The manifest declares `./skills/` and `./.lsp.json`. Agents remain in the plugin-root `agents/` directory and are described here rather than declared in `.claude-plugin/plugin.json` because this repository's manifest rules prohibit an `agents` key. Local JDTLS support files live beside the manifest at the plugin root.

## Plugin Layout

```text
plugins/java/
├── .claude-plugin/plugin.json
├── .lsp.json
├── README.md
├── agents/
│   └── java-architect.md
├── scripts/
│   ├── has-lombok.sh
│   ├── jdtls-wrapper.sh
│   └── test-jdtls-wrapper.sh
└── skills/
    ├── java-dependency-versioning/
    ├── java-language-design/
    ├── java-language-syntax/
    ├── java-performance-concurrency/
    └── java-test/
```

## Shipped Surfaces

- The plugin ships five reusable Java skills under `skills/`.
- `.lsp.json` and `scripts/jdtls-wrapper.sh` expose the Java language-server surface for Claude-compatible local development.
- `scripts/has-lombok.sh` supports Lombok source selection for the wrapper, and `scripts/test-jdtls-wrapper.sh` verifies wrapper behavior.
- The plugin ships one plugin-root agent: `java-architect` for Java language, testing, dependency, performance, and API design decisions.
- The plugin does not ship commands, hooks, MCP servers, or custom runtime data surfaces.

## Design Principles

- Keep Java guidance LTS-aware while still naming current-language behavior when it changes implementation choices.
- Prefer implementation-ready examples for core Java, tests, dependencies, and performance work over catalog-style API summaries.
- Route framework-specific behavior to Spring, Jakarta EE, or other plugin guidance instead of hiding framework assumptions in shared Java skills.
- Use `java-architect` for cross-skill tradeoffs rather than duplicating architecture guidance inside every Java skill.

## Installation

Install from Sinon:

```sh
/plugin install java@sinon
```

For local development:

```sh
claude --plugin-dir /path/to/sinon/plugins/java
```

## Scope Notes

This plugin intentionally focuses on shared, implementation-oriented skill coverage. It does not yet ship:

- custom MCP servers
- hooks
- framework-specific Spring or Jakarta EE specializations

Dependency lookup guidance can identify the artifact and verification path offline, but current-release claims require a live registry check before hardcoding version text.

## Java LSP Setup

Use JDTLS when the task needs Java symbol navigation, diagnostics, or refactors. Do not treat it as a substitute for the skills above: the skills explain how to reason about Java work, while JDTLS provides editor intelligence for `.java` files.

### Requirements

- `jdtls` executable available on `PATH`
- Java runtime compatible with the local `jdtls` installation

## LTS Version Framing

- Treat Java version-difference guidance in this plugin as LTS-first: `8`, `11`, `17`, `21`, and `25`.
- Treat `java.base` coverage here as foundational Java SE standard-library guidance rather than as a claim about broader `jdk.*` tooling or diagnostics.
- Use the Java plugin for code-shape questions tied to core packages such as `java.lang`, `java.util`, `java.time`, `java.io`, `java.nio`, `java.net`, and related `java.base` SPI families.
- `jdeps`, `jlink`, `jpackage`, runtime images, packaging chains, and live JVM diagnostics are outside this plugin's scope.

### Lombok Source Selection

The wrapper selects a Lombok source at startup in this order:

1. Explicit override jar from `JAVA_ASSISTANT_LOMBOK_JAR`, `JDK_ASSISTANT_LOMBOK_JAR` (legacy alias), or `LOMBOK_JAR`
2. Compatible project jar discovered from `.classpath` or `.factorypath` only when `JAVA_ASSISTANT_LOMBOK_PROJECT_JAR_ENABLED=true`

The wrapper chooses the effective Lombok jar at startup. Explicit override jars are trusted user configuration and work by default. Project-local jar discovery is disabled by default because a discovered jar is executable `-javaagent` code. This plugin does not ship its own fallback Lombok jar.

> [!WARNING]
>
> Project-discovered Lombok jars are trusted executable code loaded as a `-javaagent`. Only enable project jar discovery in trusted repositories and workspaces. When Lombok support is needed in an untrusted context, prefer an explicit trusted override jar via one of the environment variables below.

To provide an explicit override jar, point one of these environment variables at a local `lombok.jar`:

- `JAVA_ASSISTANT_LOMBOK_JAR` (preferred)
- `JDK_ASSISTANT_LOMBOK_JAR` (legacy compatibility alias)
- `LOMBOK_JAR`

The wrapper injects the selected jar through `JDK_JAVA_OPTIONS=-javaagent:...`.

To opt into project jar discovery for trusted workspaces, set `JAVA_ASSISTANT_LOMBOK_PROJECT_JAR_ENABLED=true` or the legacy alias `JDK_ASSISTANT_LOMBOK_PROJECT_JAR_ENABLED=true`.

Project detection signals used to prefer a project jar when it can be resolved:

- `pom.xml` with `org.projectlombok` or `lombok`
- `build.gradle` or `build.gradle.kts` with Lombok coordinates, Lombok plugins, or common Lombok dependency configurations
- `gradle/libs.versions.toml` with Lombok aliases or coordinates
- `.classpath` or `.factorypath` entries mentioning Lombok

The wrapper first checks the discovered project root and then scans nested Maven, Gradle, and Eclipse metadata files under that root while skipping common build output directories.

If no override is supplied and no compatible project jar can be resolved, the wrapper starts plain `jdtls` without Lombok support. Set `JAVA_ASSISTANT_LOMBOK_ENABLED=false` to disable Lombok selection entirely. `JDK_ASSISTANT_LOMBOK_ENABLED` is a legacy compatibility alias for the same setting.

For safety, override jar paths must be single filesystem tokens without whitespace.
