---
name: java-language-syntax
description: >-
  This skill should be used when the user asks to "explain Java syntax", "compare Java versions", "rewrite code for an older Java version", "use switch expressions", "use var", "explain records or pattern matching", or needs guidance on Java syntax and expression differences across language versions.
---

# Java Language Syntax

## Overview

Use this skill to explain Java syntax, expression forms, and version-specific language differences that affect how code is written, read, or refactored. The common case is checking the target Java baseline first, then choosing the clearest stable syntax available on that baseline. Focus on syntax and expression shape, not on higher-level API design.

## Use This Skill When

- You need to know whether a syntax form is available on a given Java baseline.
- You are rewriting Java code for an older or newer LTS target.
- You need a direct example of classic versus newer Java syntax.
- Do not use this skill when the main issue is API or type-modeling semantics rather than syntax availability.

## Common-Case Workflow

1. Identify the target Java baseline before recommending any syntax change.
2. Read the current code and decide whether the question is about stable syntax, preview syntax, or migration compatibility.
3. Prefer stable language features by default and call out preview-only constructs explicitly.
4. Recommend the smallest syntax change that improves clarity while remaining compatible with the target baseline.

## First Runnable Commands or Code Shape

Start from one version-aware syntax comparison:

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

## Ready-to-Adapt Templates

Local variable inference:

```java
var count = users.size();
```
Use when: the Java baseline supports `var` and the type remains readable from the right-hand side.

Switch expression:

```java
var result = switch (status) {
    case OK -> "ok";
    case FAIL -> "fail";
    default -> "unknown";
};
```
Use when: the target baseline supports stable switch expressions and the expression form is clearer than a statement block.

Record:

```java
record Point(int x, int y) {
}
```
Use when: the type is mostly data and the baseline supports records.
Use when: record syntax is available and the modeling choice has already been made.

Pattern matching for `instanceof`:

```java
if (obj instanceof String s) {
    use(s);
}
```
Use when: the target baseline supports pattern matching for `instanceof`.

## Validate the Result

Validate the common case with these checks:

- the target Java baseline is explicit
- the recommended syntax is stable on that baseline
- a simpler fallback exists when the baseline is older
- the newer syntax materially improves clarity rather than being newer for its own sake

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| exact LTS-boundary availability for records, pattern matching, or later syntax | `./references/syntax-version-notes.md` |

## Invariants

- MUST identify the target Java version before recommending version-sensitive syntax.
- MUST distinguish stable language features from preview-only features.
- SHOULD prefer stable syntax when the user does not explicitly ask for preview features.
- MUST explain fallback forms when recommending syntax unavailable on the target baseline.
- SHOULD focus on syntax and expression differences that materially affect code shape.
- MUST avoid presenting preview or withdrawn features as generally safe defaults.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| suggesting syntax without naming the Java baseline | compatibility becomes guesswork | anchor every syntax recommendation to the target baseline |
| treating preview features as default modernization | build and operational complexity rise unexpectedly | stay on stable syntax unless preview use is explicitly requested |
| replacing every old form with a new one mechanically | readability can get worse | upgrade only where the newer form is clearly better |
| omitting a fallback for older baselines | migration guidance becomes incomplete | show the simpler compatible form too |

## Scope Boundaries

- Activate this skill for:
  - Java grammar and syntax choices
  - expression-form differences across Java versions
  - version-aware language feature explanation
- Do not use this skill as the primary source for:
  - public API or type-modeling decisions
  - JUnit structure or test-first workflow
  - performance or concurrency tradeoffs
  - standard JDK tool selection or packaging workflows
