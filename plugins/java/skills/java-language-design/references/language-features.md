---
title: Java Language Features Reference
description: >-
  Reference for Java language feature tradeoffs, record vs class vs sealed modeling,
  enum vs sealed variants, and preview-feature cost assessment.
---

Open this reference when the Java baseline is already known and the remaining blocker is language-feature tradeoff rather than syntax availability lookup. This file should be sufficient on its own to finish one semantic-modeling decision.

Use this file to finish one of these jobs:

- decide whether a value-like type should be a record or a regular class
- decide whether a domain is closed enough for sealed modeling
- decide whether enum or sealed variants communicate the model more clearly
- pressure-test whether a preview-only feature is worth the baseline or support cost

## Official references

- Oracle Java SE documentation hub: <https://docs.oracle.com/en/java/>
- Oracle Java API documentation index: <https://docs.oracle.com/en/java/javase/index.html>
- OpenJDK JEP index: <https://openjdk.org/jeps/0>

## Semantic modeling comparisons

Mutable class: fields can change, and equality remains whatever the class explicitly implements.

```java
final class Money {
    private String currency;
    private long cents;

    Money(String currency, long cents) {
        this.currency = currency;
        this.cents = cents;
    }

    String currency() {
        return currency;
    }

    long cents() {
        return cents;
    }
}
```

Record: components are final, accessors are generated, and equality is value-based.

```java
record Money(String currency, long cents) {
}
```

Use when: the type is primarily a value carrier with stable components, value semantics, and no identity-bearing lifecycle.

Record with compact invariant validation:

```java
record Percentage(int value) {
    Percentage {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("value must be between 0 and 100");
        }
    }
}
```

Regular class with evolving state:

```java
final class RetryBudget {
    private int remaining;

    RetryBudget(int remaining) {
        this.remaining = remaining;
    }

    void consume() {
        remaining -= 1;
    }
}
```

Use when: a record still fits if the invariant is construction-time only; stay with a regular class once the type owns evolving state or identity-bearing behavior.

Sealed hierarchy: the compiler can enforce the closed set of permitted implementations.

```java
sealed interface PaymentResult permits Approved, Rejected {
}

record Approved(String authorizationId) implements PaymentResult {
}

record Rejected(String reason) implements PaymentResult {
}
```

Open interface: implementations can come from other packages or modules.

```java
public interface PaymentResult {
}

public final class Approved implements PaymentResult {
    private final String authorizationId;

    public Approved(String authorizationId) {
        this.authorizationId = authorizationId;
    }
}

public final class Rejected implements PaymentResult {
    private final String reason;

    public Rejected(String reason) {
        this.reason = reason;
    }
}
```

Use when: sealed types fit a closed domain owned by the module, while an open interface with externally defined implementations fits a model that must remain extensible across package or module boundaries.

Enum: singleton states with no per-variant payload.

```java
enum JobState {
    QUEUED,
    RUNNING,
    FAILED,
    SUCCEEDED
}
```

Sealed variants: each branch can carry a different data shape.

```java
import java.time.Duration;

sealed interface JobOutcome permits Success, Failure {
}

record Success(Duration runtime) implements JobOutcome {
}

record Failure(String code, String message) implements JobOutcome {
}
```

Use when: enum fits singleton states with no per-variant payload, while sealed variants fit cases where each branch carries different data.

Preview feature gate:

Use a placeholder instead of copying preview-sensitive syntax into ordinary examples.

```java
switch (value) {
    default -> handleDefault(value);
}
```

Use when: the team is considering a preview-only construct. Treat it as a product and support decision, not as default design modernization.

## Review questions

- Does a record model value semantics more clearly than a mutable class here?
- Is a sealed hierarchy genuinely closed inside the module, or is future extension still expected?
- Would a preview-only construct make the public API or operational baseline harder to support?

## Guidance

- Mention tradeoffs between older class hierarchies and newer record or sealed-type models.
- Prefer public API choices that remain understandable on the repository's actual Java baseline.
- Treat preview features as an explicit product decision, not a default design move.

## Checklist

- Does the proposed type carry immutable value semantics, or does it own mutable lifecycle state?
- Is the variant set truly closed inside this module or repository boundary?
- Do different branches need different payloads, making enum too weak?
- Would a preview feature leak operational or support cost into the public contract?
