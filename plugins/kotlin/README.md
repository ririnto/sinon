---
description: >-
  Overview of the Kotlin plugin, its included skills, and practical Kotlin workflow coverage.
metadata:
  reference:
    Claude Code plugins:
      url: https://code.claude.com/docs/en/plugins/manifest-reference.md
    OpenAI Codex plugins:
      url: https://developers.openai.com/plugins/build/plugins.md
---

# Kotlin

Kotlin provides language, coroutine, and testing skills with local Kotlin LSP integration.

## Included Skills

| Skill | Job | Trigger |
| --- | --- | --- |
| `kotlin-language-patterns` | Idiomatic Kotlin syntax, null safety, value modeling, collections, Java interoperability decisions | "Kotlin idioms", "null-safety design", "value types", "Java interop" |
| `kotlin-coroutines-flows` | Structured concurrency, `suspend` vs `Flow`, cancellation, scope ownership, async boundary design | "coroutine design", "Flow semantics", "async boundaries", "cancellation handling" |
| `kotest` | Kotlin test scope, coroutine-aware testing, mocking boundaries, deterministic test structure | "Kotlin unit tests", "coroutine testing", "test scope", "deterministic async" |

## Included Agents

This plugin ships no agents.

## Skill Selection

### Applying the skills

Select the skill for the current decision instead of following a language, coroutine, and testing sequence.
Use deterministic native tests when acceptance criteria or regression risks require new evidence.
Keep Java, JVM, and Spring-specific behavior outside Kotlin guidance.

### Scope boundaries

Pure Kotlin unit tests, coroutine tests, and test-structure decisions that do not require Spring
context stay in Kotlin-focused guidance.
Tests that depend on Spring Boot test slices, Spring-managed wiring, or Spring infrastructure
behavior are outside Kotlin-focused guidance.

These topics fall outside Kotlin's scope:

- Java syntax rules, Java API design, and Java-specific build conventions.
- JVM tools, JVM diagnostics, and GC analysis.
- Spring annotations, WebFlux framework wiring, Spring Boot testing, and framework-managed reactive behavior.

Spring-specific coroutine endpoints, reactive controllers, and `WebClient` usage are outside Kotlin guidance.

## Runtime Model

Claude Code loads this package through the Sinon marketplace entry and its default component paths.
Codex uses `.codex-plugin/plugin.json` to load skills from the default `skills/` directory.
Claude Code also provides the Kotlin LSP integration described below.
The `.lsp.json` language-server integration remains a Claude Code surface.
Local language-server support files live at the package root.

## Plugin Layout

```text
plugins/kotlin/
+-- .codex-plugin/plugin.json
+-- .lsp.json
+-- README.md
+-- skills/
    +-- kotlin-coroutines-flows/
    +-- kotlin-language-patterns/
    +-- kotest/
```

## Shipped Surfaces

- The plugin ships three reusable Kotlin skills under `skills/`.
- `.lsp.json` and the Kotlin language-server integration expose editor intelligence for `.kt` and `.kts` files.

## Kotlin LSP Setup

This plugin uses `kotlin-lsp` as the Kotlin language-server surface.

### Requirements

- `kotlin-lsp` executable available on `PATH`

Use kotlin-lsp when the task needs symbol navigation, diagnostics, or safe refactors in `.kt` files.
Do not treat it as a substitute for the skills above: the skills explain how to reason about Kotlin work, while kotlin-lsp provides editor intelligence.

## Installation

Install from Sinon:

```sh
claude plugin install kotlin@sinon
```

For local development:

```sh
claude --plugin-dir /path/to/sinon/plugins/kotlin
```

## Scope Notes

This plugin intentionally focuses on Kotlin-native language patterns, coroutine and Flow design, and Kotlin testing guidance.
It does not yet ship custom MCP servers or hooks.
Dependency lookup, Java syntax rules, Spring framework behavior, and JVM diagnostics are outside its scope.
