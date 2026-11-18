---
metadata:
  reference:
    JEP 454:
      version: JDK 22
      url: https://openjdk.org/jeps/454
    JEP 484:
      version: JDK 24
      url: https://openjdk.org/jeps/484
name: java-language-syntax
description: >-
  Check Java syntax compatibility, migrate source between Java baselines, or choose foundational java.base APIs.
---

# Java Language Syntax

Explain Java syntax, LTS-boundary language differences, and foundational `java.base` coverage when it materially affects how code is written, read, or refactored.
The common case is checking the target Java LTS baseline first, then choosing the clearest stable syntax and the smallest baseline-safe standard-library surface available on that baseline.

## Operating rules

- MUST identify the actual target Java version before recommending any version-sensitive syntax.
- MUST distinguish stable language features from preview-only or withdrawn features.
- SHOULD prefer stable syntax unless preview use is explicitly requested.
- MUST explain fallback forms when recommending syntax unavailable on the target baseline.
- MUST treat string templates as withdrawn (previewed in JDK 21 and 22, then withdrawn before JDK 23 instead of being finalized), not as a valid Java 25 default or modernization path.
- MUST keep version-difference guidance centered on LTS releases unless the user explicitly asks about a non-LTS release.
- MUST treat `java.base` guidance here as foundational standard-library coverage, not as a claim about broader Java SE modules or JDK tooling.
- SHOULD focus on syntax and expression differences that materially affect code shape.
- SHOULD prefer the smallest newer syntax that materially improves readability over mechanically replacing every older form.
- MUST NOT rely on `Collectors.toList()` for a particular list implementation, mutability, serializability, or thread safety.
  Use `Collectors.toCollection(ArrayList::new)` when mutability matters; for an unmodifiable result use `Collectors.toUnmodifiableList()` (JDK 10+) or `Stream.toList()` (JDK 16+).

## Task Context

Read the relevant source and build configuration to establish the target baseline.
Ask only when the baseline remains unclear and changes the recommendation.
Use [`advanced-syntax-recipes.md`](./references/advanced-syntax-recipes.md) for exact LTS availability, migration, or later-LTS recipes.
Use [`java-base-family-map.md`](./references/java-base-family-map.md) to select a foundational package family before adding dependencies.

### Version legend

- `(JDK 8+)` means safe on Java 8 and later LTS targets.
- `(JDK 11+)` means available on Java 11 and later.
- `(JDK 17+)`, `(JDK 21+)`, `(JDK 25+)` mean an LTS-boundary upgrade, not a universal fallback.
- If two examples solve the same problem, prefer the lowest-baseline version that still keeps the code clear.

## Syntax Examples

Version-aware switch comparison `(JDK 17+)` vs fallback `(JDK 8+)`:

```java
String result = switch (status) {
    case OK -> "ok";
    case FAIL -> "fail";
    default -> "unknown";
};
```

```java
String result;
switch (status) {
    case OK:
        result = "ok";
        break;
    case FAIL:
        result = "fail";
        break;
    default:
        result = "unknown";
}
```

Use when: you need a quick answer to "can I use this on Java X?"

Text block for multiline literals `(JDK 17+)`:

```java
String sql = """
    select *
    from users
    where active = true
    order by created_at desc
    """;
```

Record `(JDK 17+)`:

```java
record Point(int x, int y) {
}
```

Use [syntax templates](./references/syntax-templates.md) when choosing version-compatible language constructs or foundational APIs, including streams, asynchronous code, HTTP, and patterns.

## Edge cases

- If repository evidence does not establish the required Java baseline, clarify it before recommending version-sensitive syntax.
- Questions about API shape or type modeling are outside this skill's scope.
  Use `java:java-language-design`.
- Questions about JUnit structure are outside this skill's scope.
  Use `java:java-test`.
- Questions about profiling or concurrency are outside this skill's scope.
  Use `java:java-performance-concurrency`.
- Questions about Maven coordinate lookup are outside this skill's scope.
  Use `java:java-dependency-versioning`.
- If a preview feature is requested, state the support cost and baseline requirement explicitly before including it in guidance.
- If `java.base` drifts toward `jdk.*` tools, `jdeps`, `jlink`, `jpackage`, runtime images, packaging chains, or live JVM diagnostics, stop and clarify that those are outside this skill's scope.

## Result

State the baseline, recommended syntax, and any relevant fallback or preview limitation.
For source edits, use the consumer module's configured release and preview settings to compile or test the changed syntax.
