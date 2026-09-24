---
description: >-
  Overview of the Kotlin plugin, its included skills, and practical Kotlin workflow coverage.
---

# Kotlin

Kotlin is a shared, skill-first plugin for Kotlin application and library work in the Sinon Claude marketplace.

## Purpose

- Provide reusable Kotlin workflows that remain portable across Claude Code plugin installations.
- Keep skills practical, example-driven, and focused on writing or reviewing real Kotlin code
  in the smallest matching Kotlin domain.
- Separate Kotlin language, coroutine, and Kotlin-native testing concerns from Java language,
  JVM tooling, and framework-specific Spring behavior.

## Included Skills

| Skill | Job | Trigger |
| --- | --- | --- |
| `kotlin-language-patterns` | Idiomatic Kotlin syntax, null safety, value modeling, collections, Java interoperability decisions | "Kotlin idioms", "null-safety design", "value types", "Java interop" |
| `kotlin-coroutines-flows` | Structured concurrency, `suspend` vs `Flow`, cancellation, scope ownership, async boundary design | "coroutine design", "Flow semantics", "async boundaries", "cancellation handling" |
| `kotest` | Kotlin test scope, coroutine-aware testing, mocking boundaries, deterministic test structure | "Kotlin unit tests", "coroutine testing", "test scope", "deterministic async" |

## Included Agents

This plugin ships no agents.

## Skill Selection

Start here when Kotlin work could fit more than one skill:

### Language patterns vs coroutines vs testing

- Use `kotlin-language-patterns` for idiomatic API shape, null-safety, and value modeling decisions.
- Use `kotlin-coroutines-flows` when the question concerns async contract, `suspend`, `Flow`, cancellation, or scope ownership.
- Use `kotest` for Kotlin unit-test structure, coroutine-aware testing, or test-scope decisions.
- When the question is "how should the async contract be shaped?", stay in coroutine guidance.
  - When it is "how do I verify that async behavior deterministically?", stay in testing guidance.

### Applying the skills

Select the skill for the current decision instead of following a language, coroutine, and testing sequence.
Use deterministic native tests when acceptance criteria or regression risks require new evidence.
Keep Java, JVM, and Spring-specific behavior outside Kotlin guidance.

### Scope boundaries

Pure Kotlin unit tests, coroutine tests, and test-structure decisions that do not require Spring
context stay in Kotlin-focused guidance.
Tests that depend on Spring Boot test slices, Spring-managed wiring, or Spring infrastructure
behavior are outside Kotlin-focused guidance.

Kotlin stays responsible for Kotlin-native language patterns, coroutine and Flow modeling,
Kotlin-focused test structure, and kotlin-lsp-assisted source analysis.

These topics fall outside Kotlin's scope:

- Java syntax rules, Java API design, and Java-specific build conventions.
- JVM tools, JVM diagnostics, and GC analysis.
- Spring annotations, WebFlux framework wiring, Spring Boot testing, and framework-managed reactive behavior.

Spring-specific coroutine endpoints, reactive controllers, and `WebClient` usage are outside Kotlin guidance.
Kotlin remains the home for general coroutine and Flow design outside Spring framework behavior.

## Design Principles

- Prefer working code shapes over generic language summaries.
- Keep examples minimal but directly adaptable to production code.
- Choose the smallest Kotlin skill that matches the task.
  Each skill owns its style and pattern rules.

## Runtime Model

This plugin uses `.claude-plugin/plugin.json` at the plugin root.
Local language-server support files live beside the manifest at the plugin root.

## Plugin Layout

```text
plugins/kotlin/
+-- .claude-plugin/plugin.json
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
