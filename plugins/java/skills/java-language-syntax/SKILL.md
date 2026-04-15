---
name: java-language-syntax
description: >-
  Use this skill when the user asks to "explain Java syntax", "compare Java LTS versions", "rewrite code for an older Java version", "use switch expressions", "use var", "explain records or pattern matching", or needs guidance on Java syntax, expression differences, or foundational `java.base` coverage across language versions.
---

# Java Language Syntax

## Overview

Use this skill to explain Java syntax, expression forms, LTS-boundary language differences, and foundational `java.base` coverage when it materially affects how code is written, read, or refactored. The common case is checking the target Java LTS baseline first, then choosing the clearest stable syntax and the smallest baseline-safe standard-library surface available on that baseline. Focus on syntax and expression shape first, and treat `java.base` guidance here as code-shape support rather than higher-level API design.

## Use This Skill When

- You need to know whether a syntax form is available on a given Java baseline.
- You are rewriting Java code for an older or newer LTS target.
- You need a direct example of classic versus newer Java syntax.
- You need to know whether a foundational `java.base` API family is a reasonable default on a given Java LTS baseline.
- Do not use this skill when the main issue is API or type-modeling semantics rather than syntax availability.

## Common-Case Workflow

1. Identify the target Java LTS baseline before recommending any syntax change.
2. Read the current code and decide whether the question is about stable syntax, preview syntax, migration compatibility, or foundational `java.base` usage that affects code shape.
3. Prefer stable language features by default and call out preview-only or withdrawn constructs explicitly.
4. When the question reaches into `java.base`, keep the answer anchored to foundational standard-library families rather than JDK tooling or packaging workflows.
5. Recommend the smallest syntax or library-shape change that improves clarity while remaining compatible with the target baseline.

## Java Syntax Decision Path

Use this quick decision path before escalating to deeper references:

- start with the target Java LTS boundary: `8`, `11`, `17`, `21`, or `25`
- start with stable syntax; call out preview or withdrawn constructs explicitly instead of treating them as the default modernization path
- prefer the smallest newer syntax that materially improves readability over mechanically replacing every older form
- keep fallback shapes visible for older baselines when the recommendation depends on `17`, `21`, or `25`
- when the question reaches into `java.base`, start with foundational families such as collections, time, files, regex, or `Optional` before suggesting extra dependencies
- keep `java.base` guidance separate from `jdeps`, `jlink`, `jpackage`, runtime images, or live JVM diagnostics

## Example Version Legend

- `(JDK 8+)` means the example is safe on Java 8 and later LTS targets.
- `(JDK 11+)` means the example depends on syntax or standard-library shapes available on Java 11 and later.
- `(JDK 17+)`, `(JDK 21+)`, and `(JDK 25+)` mean the example should be treated as an LTS-boundary upgrade, not as a universal fallback.
- If two examples solve the same problem, prefer the lowest-baseline version that still keeps the code clear.

## First Runnable Commands or Code Shape

Start from one version-aware syntax comparison.

Version-aware switch comparison `(JDK 17+)` vs fallback `(JDK 8+)`:

```java
var result = switch (status) {
    case OK -> "ok";
    case FAIL -> "fail";
    default -> "unknown";
};
```

Fallback shape for older baselines:

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
var sql = """
    select *
    from users
    where active = true
    order by created_at desc
    """;
```
Use when: the baseline is Java 17+ and the literal is clearer as real multiline text than as concatenated strings.

Record `(JDK 17+)`:

```java
record Point(int x, int y) {
}
```
Use when: the type is mostly data and the baseline supports records.

## Ready-to-Adapt Templates

Local variable inference `(JDK 11+)`:

```java
var count = users.size();
```
Use when: the Java baseline supports `var` and the type remains readable from the right-hand side.

Switch expression `(JDK 17+)`:

```java
var result = switch (status) {
    case OK -> "ok";
    case FAIL -> "fail";
    default -> "unknown";
};
```
Use when: the target baseline supports stable switch expressions and the expression form is clearer than a statement block.

Classic switch fallback `(JDK 8+)`:

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
Use when: the target baseline is Java 8 or Java 11 and switch expressions are not stable yet.

Text block `(JDK 17+)`:

```java
var payload = """
    {
      "enabled": true,
      "name": "demo"
    }
    """;
```
Use when: the baseline is Java 17+ and the literal is easier to read as structured text.

Sealed hierarchy `(JDK 17+)`:

```java
sealed interface PaymentResult permits Approved, Rejected {
}

record Approved(String authorizationId) implements PaymentResult {
}

record Rejected(String reason) implements PaymentResult {
}
```
Use when: the baseline is Java 17+ and the domain should stay closed to a known set of variants.

Pattern matching for `instanceof` `(JDK 17+)`:

```java
if (obj instanceof String s) {
    use(s);
}
```
Use when: the target baseline supports pattern matching for `instanceof`.

Pattern-matching switch `(JDK 21+)`:

```java
return switch (shape) {
    case Circle c -> c.radius();
    case Rectangle r -> r.width() * r.height();
};
```
Use when: the baseline is Java 21+ and exhaustive matching is clearer than chained casts or `if/else` blocks.

Unnamed pattern `(JDK 25+)`:

```java
if (obj instanceof Order(String id, _, var total)) {
    audit(id, total);
}
```
Use when: the baseline is Java 25+ and some deconstructed values do not add meaning.

`Optional` pipeline `(JDK 8+)`:

```java
return Optional.ofNullable(config)
    .map(Config::timeout)
    .filter(timeout -> timeout > 0)
    .orElseThrow(() -> new IllegalStateException("timeout must be set"));
```
Use when: the problem is a small return-value or configuration pipeline rather than a field model.

Immutable collection factory `(JDK 9+, practical LTS use: JDK 11+)`:

```java
var roles = List.of("reader", "writer");
```
Use when: the list is fixed at creation time and the code benefits from an immutable factory rather than mutable setup.

## Validate the Result

Validate the common case with these checks:

- the target Java baseline is explicit
- the recommended syntax is stable on that baseline
- a simpler fallback exists when the baseline is older
- any `java.base` recommendation stays inside foundational standard-library coverage rather than drifting into `jdk.*` tools or packaging advice
- preview-sensitive or withdrawn constructs are marked explicitly instead of being smuggled in as normal style

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| exact LTS-boundary availability, baseline comparison, upgrade/downgrade planning, or later-LTS syntax recipes | [`advanced-syntax-recipes.md`](./references/advanced-syntax-recipes.md) |
| the question is really about choosing a foundational `java.base` package family first | [`java-base-family-map.md`](./references/java-base-family-map.md) |

## Invariants

- MUST identify the target Java LTS version before recommending version-sensitive syntax.
- MUST distinguish stable language features from preview-only features.
- SHOULD prefer stable syntax when the user does not explicitly ask for preview features.
- MUST explain fallback forms when recommending syntax unavailable on the target baseline.
- SHOULD focus on syntax and expression differences that materially affect code shape.
- MUST avoid presenting preview or withdrawn features as generally safe defaults.
- MUST treat string templates as withdrawn rather than as a valid Java 25 default or modernization path.
- MUST keep version-difference guidance centered on LTS releases unless the user explicitly asks about a non-LTS release.
- MUST treat `java.base` guidance here as foundational standard-library coverage, not as a claim about broader Java SE modules or JDK tooling.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| suggesting syntax without naming the Java baseline | compatibility becomes guesswork | anchor every syntax recommendation to the target baseline |
| treating preview features as default modernization | build and operational complexity rise unexpectedly | stay on stable syntax unless preview use is explicitly requested |
| treating string templates as if they were a stable Java 25 feature | the recommendation points users toward a withdrawn path | treat string templates as unavailable for default guidance and keep them out of LTS upgrade advice |
| replacing every old form with a new one mechanically | readability can get worse | upgrade only where the newer form is clearly better |
| omitting a fallback for older baselines | migration guidance becomes incomplete | show the simpler compatible form too |
| treating `java.base` as if it covered all `jdk.*` tools or every Java SE module | library guidance and tooling guidance get mixed together | keep `java.base` here as foundational library coverage and route tooling questions to the JDK plugin |

## Scope Boundaries

- Activate this skill for:
  - Java grammar and syntax choices
  - expression-form differences across Java LTS versions
  - version-aware language feature explanation
  - foundational `java.base` package-family guidance when it affects code shape or baseline-safe examples
- Do not use this skill as the primary source for:
  - public API or type-modeling decisions
  - JUnit structure or test-first workflow
  - performance or concurrency tradeoffs
  - broader Java SE module coverage outside `java.base`
  - standard JDK tool selection or packaging workflows
