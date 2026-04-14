---
name: java-language-design
description: >-
  This skill should be used when the user asks to "design a Java API", "review Java class structure", "refactor Java code", "decide whether records or sealed classes fit the model", "choose checked vs unchecked exceptions", or needs guidance on idiomatic Java language and library design.
---

# Java Language Design

## Overview

Use this skill to produce idiomatic, maintainable Java code and API designs. The common case is choosing a clearer type shape, a narrower contract, and an unsurprising exception and mutability model before touching framework details. Make ownership, value semantics, and failure behavior explicit to callers.

## Use This Skill When

- You are shaping a public or widely used Java API.
- You are refactoring class hierarchy, mutability, or exception strategy.
- You need a default design shape for records, sealed types, interfaces, factories, or collection exposure after the baseline question is already settled.
- Do not use this skill when the main issue is syntax availability across Java versions rather than design semantics.

## Common-Case Workflow

1. Read the target class and the nearest related tests or callers first.
2. Identify the Java baseline and whether the surface is internal or externally consumed.
3. Choose the smallest design change that improves clarity while preserving the requested behavior.
4. Make value semantics, exception contracts, and mutability boundaries explicit before adding extra abstraction.

## First Runnable Commands or Code Shape

Start from one explicit value carrier and one explicit capability interface:

```java
public record CustomerId(String value) {
}

public interface PaymentGateway {
    Receipt charge(ChargeRequest request);
}
```
Use when: tightening a contract or replacing a vague mutable DTO or service surface.

## Ready-to-Adapt Templates

Immutable value carrier:

```java
public record CustomerId(String value) {
}
```
Use when: the type is immutable data and the Java baseline supports records.

Factory for clearer invariants:

```java
public final class RetryPolicy {
    private final int maxAttempts;

    private RetryPolicy(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public static RetryPolicy of(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        return new RetryPolicy(maxAttempts);
    }
}
```
Use when: constructor overloading would obscure validation or naming.

Read-only collection exposure:

```java
public List<String> roles() {
    return List.copyOf(roles);
}
```
Use when: callers should read a collection without mutating internal storage.

Checked vs unchecked exception rule:

```java
public Receipt load(String id) throws IOException {
    return gateway.load(id);
}
```
Use when: the failure mode is part of a real caller contract rather than a generic programming failure.

## Validate the Result

Validate the common case with these checks:

- the public surface is narrower and clearer than before
- value semantics, mutability, and visibility are explicit
- collection-returning methods do not leak internal mutable state
- checked exceptions remain only where callers can meaningfully recover

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| records, sealed classes, and semantic modeling tradeoffs once the Java baseline is known | `./references/language-features.md` |
| mutability, collection exposure, visibility, or exception-contract review | `./references/api-design.md` |

## Invariants

- SHOULD prefer value semantics where possible.
- MUST keep public APIs narrow and intention-revealing.
- MUST expose immutable views unless mutation is part of the contract.
- SHOULD prefer simple, explicit contracts over inheritance-heavy designs.
- SHOULD check whether records, sealed classes, enums, or interfaces fit the model better than ordinary classes.
- MUST use checked exceptions sparingly and only when callers can meaningfully recover.
- MUST keep constructors and factories explicit about invariants.
- MUST avoid leaking implementation types in public signatures.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| returning concrete mutable collections directly | callers can accidentally mutate internal state | return interfaces and defensive copies where needed |
| using inheritance when the model is just data or capability | the contract becomes harder to understand | choose record, enum, or interface first |
| throwing checked exceptions for non-recoverable failures | callers get noise without real recovery paths | reserve checked exceptions for meaningful recovery contracts |
| hiding invariants inside overloaded constructors | object creation rules become unclear | use named factories or explicit validation |

## Scope Boundaries

- Activate this skill for:
  - API shape and contract clarity
  - language-level type and modeling decisions
  - mutability, ownership, and exception decisions
- Do not use this skill as the primary source for:
  - Java grammar and syntax-form compatibility questions
  - JUnit structure or test-first workflow
  - performance tuning or concurrency model selection
  - standard JDK tool selection or packaging workflows
