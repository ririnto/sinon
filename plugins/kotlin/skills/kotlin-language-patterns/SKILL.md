---
metadata:
  reference:
    Kotlin:
      version: 2.4.20
      url: https://github.com/JetBrains/kotlin/releases/tag/v2.4.20
    Kotlin EAP documentation:
      url: https://kotlinlang.org/docs/_llms/eap.txt
    Kotlin release notes:
      - version: 2.2.0
        url: https://kotlinlang.org/docs/_llms/whatsnew22.txt
      - version: 2.3.0
        url: https://kotlinlang.org/docs/_llms/whatsnew23.txt
      - version: 2.4.0
        url: https://kotlinlang.org/docs/_llms/whatsnew24.txt
    Kotlin standard library API:
      url:
        - https://kotlinlang.org/api/core/kotlin-stdlib/
        - https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/require-not-null.html
        - https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/check-not-null.html
        - https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/to-regex.html
        - https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/-match-group-collection/
    Kotlin scope functions:
      url:
        - https://kotlinlang.org/docs/_llms/scope-functions.txt
        - https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/take-if.html
    Kotlin collection builders:
      url: https://kotlinlang.org/docs/constructing-collections.html
    Kotlin JVM path extensions:
      url: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/
    Kotlin Path composition:
      version: Kotlin 1.5+
      url: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/div.html
    kotlinx-datetime:
      version: 0.8.0
      url: https://raw.githubusercontent.com/Kotlin/kotlinx-datetime/v0.8.0/README.md
    kotlinx.serialization Instant support:
      version: 1.9.0
      url: https://raw.githubusercontent.com/Kotlin/kotlinx.serialization/v1.9.0/CHANGELOG.md
    kotlinx.serialization Instant component serializer:
      version: 1.11.0
      url: https://raw.githubusercontent.com/Kotlin/kotlinx.serialization/v1.11.0/core/commonMain/src/kotlinx/serialization/builtins/InstantComponentSerializer.kt
    Java Path API:
      version: Java SE 25
      url: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Path.html
name: kotlin-language-patterns
description: >-
  Design or refactor Kotlin types, null handling, collections, extensions, Java interop, or stdlib boundaries.
---

# Kotlin Language Patterns

## Goal

Choose the smallest construct that preserves meaning.
Minimum Kotlin 2.1; 2.4.20 is an example, not an upgrade requirement.
For compiler upgrades compare stable releases excluding EAP and confirm compatibility.
`kotlin.io.encoding`, `kotlin.time.Instant`, and `kotlin.uuid` are stable since Kotlin 2.2, 2.3, and 2.4 respectively.
On Kotlin 2.1, use compatible `kotlinx.datetime.Instant` 0.6.x or the 0.7.x compatibility release for real moments.
Current `kotlinx-datetime` uses `kotlin.time.Instant` and `kotlin.time.Clock`; date-time and serialization examples require Kotlin 2.3 or later.
Use compatible managed serialization and datetime versions; for new dependencies or upgrades check Maven Central for the latest stable Kotlin-compatible version and update the catalog.

## Operating Rules

- MUST make public API nullability explicit, never use `!!` in production, and validate at boundaries.
- MUST pin Java platform-type nullability at the interop edge; never propagate `T!` inward.
- SHOULD prefer `val` without implying immutable objects; preserve evaluation order and count, exception timing, mutable snapshots, and closure capture.
- SHOULD choose the smallest domain type and follow target conventions.
- SHOULD use `?.let` for present-only work, `?:` for defaults or required-value failures, and `requireNotNull` for arguments or `checkNotNull` for state instead of Boolean null checks; preserve exception types, messages, evaluation order, wrapper contracts, and `Unit` returns.
- SHOULD invert guards to keep the main path positive instead of `return`, `break`, or `continue` only when behavior is unchanged and no nesting or mutable state is added.
- SHOULD use `when (subject)` for one branching value; preserve evaluation order, nullability, smart casts, side effects, and eagerness when changing predicates.
- SHOULD use callable references for simple `map`, `filter`, and similar lambdas, or pass an existing reference instead of wrapping it, only when meaning, evaluation, receiver binding, and overload resolution remain unchanged.
- SHOULD prefer `kotlin.io.path.div` for JVM `Path` composition when stdlib is available, read-only public collection interfaces, string helpers before `Regex`, and `String.toRegex()` when needed.
- SHOULD keep short calls within one line when they fit and use infix functions only when natural and unambiguous.
- Name lambda parameters when this clarifies their role or avoids ambiguity.
- SHOULD document every effectively public declaration with KDoc, considering enclosing visibility; private helpers and local callbacks need no documentation unless their contract is surprising.
- SHOULD prefer `tailrec` over loops only for real tail calls with preserved semantics and readability.
- SHOULD keep collection pipelines eager unless `Sequence` materially helps; prefer simple `map`, `filter`, `associate`, and `groupBy` when equally clear, and name intermediate values when chains obscure meaning.
- MUST confine local result-building mutation to `buildList`, `buildSet`, or `buildMap` lambdas and prevent receiver escape. This excludes owned long-lived mutable state, stateful delegates, recursive visited sets, and algorithmic worklists.
- Use `runCatching` and `Result` when a boundary needs failures as values; propagate `CancellationException` in suspending code.
- MUST preserve applicable Java interoperability and identify JVM-only or experimental APIs inline.
- SHOULD keep a stable member scan order. Model required and optional constructor dependencies to the container and application contract; fail clearly if required dependencies are missing.

## Task Context

Read declarations and callers; preserve behavior and baseline.

## References

Read relevant references.

| Open when... | Read... |
| --- | --- |
| choosing value, data, regular, object, enum, or sealed types | `./references/language-modeling.md` |
| resolving null-heavy paths or tangled scope functions | `./references/null-safety-and-scope-functions.md` |
| choosing builders, splitting predicates, or evaluating laziness | `./references/collections-and-sequences.md` |
| implementing JVM filesystem or resource handling | `./references/path-filesystem.md` |
| modeling timestamps, dates, or civil time | `./references/datetime-modeling.md` |
| implementing `Json`, defaults, or contextual serializers | `./references/serialization-patterns.md` |
| checking stdlib portability or API status | `./references/stdlib-boundaries.md` |
| handling nulls, validation, smart casts, lambda captures, types, generics, extensions, delegation, or member order | `./references/language-decisions.md` |
| choosing collection, scope, string, regex, or Result patterns | `./references/collections-strings-results.md` |
| designing Java interop or serialization, time, or filesystem boundaries | `./references/java-and-native-boundaries.md` |

## Core Decisions

Use `require` for arguments and `check` for state; JVM `assert` is only for skippable internal checks.
Extension dispatch is static; use members for runtime polymorphism.
Data class `copy()` is shallow; return snapshots to protect mutable state.
Prefer `try/catch` over `Result` for exception-specific handling, `finally`, or cleanup; rethrow unexpected `recover` failures.
Use `kotlinx.serialization` for Kotlin-first encoding, `kotlinx.datetime` for civil time, and JVM `Path` with `kotlin.io.path` for filesystem work.

## Completion

Compile or test the affected caller.
Explain changes to observable types, nullability, ordering, or interop when they affect that caller.

## Scope Boundaries

Coroutines, Flow, tests, and runtime diagnostics are outside scope.
