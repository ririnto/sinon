---
title: Kotlin
description: >-
  Overview of the Kotlin plugin, its included skills, and practical Kotlin workflow coverage.
---

Kotlin is a shared, skill-first plugin for Kotlin application and library work in the Sinon universal marketplace.

## Purpose

- Provide reusable Kotlin workflows that remain portable across Claude Code and Codex-style plugin systems.
- Keep skills practical, example-driven, and focused on writing or reviewing real Kotlin code in the smallest matching Kotlin domain.
- Separate Kotlin language, coroutine, and Kotlin-native testing concerns from Java language, JVM tooling, and framework-specific Spring behavior.

## Included Skills

- `kotlin-language-patterns`: idiomatic Kotlin syntax, null safety, value modeling, collections, and Java interoperability decisions.
- `kotlin-coroutines-flows`: structured concurrency, `suspend` versus `Flow`, cancellation, scope ownership, and async boundary design.
- `kotlin-test`: Kotlin test scope, coroutine-aware testing, mocking boundaries, and deterministic test structure.

## When to Use Which Skill

- If the question is "how should the async contract be shaped?", stay in coroutine guidance; if it is "how do I verify that async behavior deterministically?", stay in testing guidance.
- Idiomatic Kotlin API shape, null-safety, and value modeling questions belong in the language-pattern guidance.
- `suspend`, `Flow`, cancellation, and async-boundary questions belong in the coroutine guidance.
- Kotlin unit-test structure and coroutine-aware test questions belong in the testing guidance.

Typical workflow:

1. Establish the type model, null-safety, and collection shape first.
2. Add coroutine and Flow guidance when async control flow, cancellation, or stream semantics matter.
3. Lock behavior with deterministic tests and the smallest correct scope.
4. Java syntax and JVM tooling questions belong in Java- or JVM-focused guidance.
5. Spring-specific coroutine controllers, Spring Boot tests, and Spring WebFlux behavior belong in Spring-focused guidance.

Testing boundary:

- Pure Kotlin unit tests, coroutine tests, and test-structure decisions that do not require Spring context stay in Kotlin-focused guidance.
- Tests that depend on Spring Boot test slices, Spring-managed wiring, or Spring infrastructure behavior belong in Spring-focused guidance.

## Scope Boundaries

Kotlin stays responsible for Kotlin-native language patterns, coroutine and Flow modeling, Kotlin-focused test structure, and kotlin-lsp-assisted source analysis.

These topics fall outside Kotlin's scope:

- Java syntax rules, Java API design, and Java-specific build conventions.
- JVM tools, JVM diagnostics, and GC analysis.
- Spring annotations, WebFlux framework wiring, Spring Boot testing, and framework-managed reactive behavior.

Spring-specific coroutine endpoints, reactive controllers, and `WebClient` usage belong to Spring-focused guidance. Kotlin remains the home for general coroutine and Flow design outside Spring framework behavior.

## Design Principles

- Prefer working code shapes over generic language summaries.
- Keep examples minimal but directly adaptable to production code.
- Choose the smallest Kotlin skill that matches the task.
- Keep `SKILL.md` self-contained and usable on its own; use `references/` only for supplemental decision aids and longer notes.
- Unnecessary blank lines inside function bodies SHOULD be removed.
- Variables used only once SHOULD be inlined when their names and extraction order do not add meaning.
- Explicit lambda parameter names SHOULD be preferred over `it` when the named form improves scanning or domain clarity.
- Class-level and member properties SHOULD spell out their types.
- Extension functions and extension properties SHOULD be used only when the receiver-centric shape makes the call site clearer than an ordinary function or member.
- Extension properties SHOULD NOT hide expensive work, mutation, or surprising derived state behind field-like syntax.
- Infix functions SHOULD be used only when the operation reads naturally at the call site and remains unambiguous without extra context.

## Runtime Model

This plugin uses one shared plugin root with two thin runtime manifests:

- `.claude-plugin/plugin.json`
- `.codex-plugin/plugin.json`

The actual reusable content lives beside those manifests at the plugin root.

## Plugin Layout

```text
plugins/kotlin/
├── .claude-plugin/plugin.json
├── .codex-plugin/plugin.json
├── .lsp.json
├── README.md
└── skills/
    ├── kotlin-coroutines-flows/
    ├── kotlin-language-patterns/
    └── kotlin-test/
```

## Shipped Surfaces

- The plugin ships three reusable Kotlin skills under `skills/`.
- Each skill may include skill-local `agents/openai.yaml` metadata for Codex/OpenAI packaging. These files are metadata attached to the skill directories, not plugin-root agents.
- `.lsp.json` exposes the Kotlin language-server surface for Claude-compatible local development.
- The plugin ships no plugin-root `agents/` directory.
- The plugin does not ship commands, hooks, MCP servers, or custom runtime data surfaces.

## Kotlin LSP Setup

This plugin uses `kotlin-lsp` as the Kotlin language-server surface.

### Requirements

- `kotlin-lsp` executable available on `PATH`

Use kotlin-lsp when the task needs symbol navigation, diagnostics, or safe refactors in `.kt` files. Do not treat it as a substitute for the skills above: the skills explain how to reason about Kotlin work, while kotlin-lsp provides editor intelligence.

## Installation

Install from Sinon:

```bash
/plugin install kotlin@sinon
```

For local development:

```bash
claude --plugin-dir /path/to/sinon/plugins/kotlin
```

## Scope Notes

This plugin intentionally focuses on Kotlin-native language patterns, coroutine and Flow design, and Kotlin testing guidance. It does not cover:

- Java syntax, Java API design, or JVM runtime diagnostics
- Spring framework wiring or Spring-managed reactive behavior
- application-framework-specific testing beyond Kotlin-native test structure
