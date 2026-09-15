---
name: java-language-design
description: >-
  Design or review Java type models, public APIs, mutability, and exception contracts.
---

# Java Language Design

Java 17+ is the ordinary baseline for records and sealed classes in this skill.
When the target project is below Java 17, keep the older-class fallback explicit at the design point.

Produce idiomatic, maintainable Java code and API designs.
The common case is choosing a clearer type shape, a narrower contract, and an unsurprising exception and mutability model before touching framework details.

## Operating rules

- SHOULD prefer value semantics where possible.
- MUST keep public APIs narrow and intention-revealing.
- MUST document every externally visible declaration with Javadoc.
  - Package-private helpers inside a published package stay undocumented unless the contract is surprising.
- MUST expose immutable views unless mutation is part of the contract.
- SHOULD prefer simple, explicit contracts over inheritance-heavy designs.
- SHOULD check whether records, sealed classes, enums, or interfaces fit the model better than ordinary classes.
- MUST use checked exceptions sparingly and only when callers can meaningfully recover.
- MUST keep constructors and factories explicit about invariants.
- MUST avoid leaking implementation types in public signatures.
- SHOULD order top-level class members as: static fields, instance fields, constructors, static methods, overridden methods, instance methods, then inner static classes/records/enums.
- SHOULD order members within each method group by visibility: `public`, `protected`, package-private, then `private`.

## Task Context

Read the target type and relevant callers or tests to establish its contract and Java baseline.
Use [`language-features.md`](./references/language-features.md) for records, sealed types, and semantic modeling decisions.
Use [`api-design.md`](./references/api-design.md) for mutability, visibility, collection exposure, and exception-contract review.
Keep proposals distinct from authorized API changes.

## Contract Example

Start from one explicit value carrier and one explicit capability interface:

```java
/**
 * Value carrier for a customer identifier.
 */
public record CustomerId(String value) {
}

/**
 * Gateway capability for charging a payment request.
 */
public interface PaymentGateway {
    Receipt charge(ChargeRequest request);
}
```

Use when: tightening a contract or replacing a vague mutable DTO or service surface.

## Ready-to-adapt templates

### Factory for clearer invariants

```java
/**
 * Immutable retry policy with a positive attempt budget.
 */
public final class RetryPolicy {
    private final int maxAttempts;

    private RetryPolicy(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    /**
     * Creates a policy that allows the given positive number of attempts.
     *
     * @throws IllegalArgumentException if {@code maxAttempts} is below 1
     */
    public static RetryPolicy of(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        return new RetryPolicy(maxAttempts);
    }
}
```

### Builder for complex construction

When a type has many optional parameters and a factory method becomes unwieldy, keep the Builder's constructor private, keep defaults visible as Builder field initializers, and validate ranges in the setter (or in `build()` when several fields interact).
Return an immutable instance from `build()`.
The model knows the pattern shape.

### equals and hashCode for non-record value types (pre-Java 17)

```java
import java.util.Objects;

/**
 * Immutable money amount in a minor-unit representation.
 */
public final class Money {
    private final String currency;
    private final long cents;

    public Money(String currency, long cents) {
        this.currency = Objects.requireNonNull(currency);
        this.cents = cents;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Money)) {
            return false;
        }
        Money money = (Money) o;
        return currency.equals(money.currency)
            && cents == money.cents;
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency, cents);
    }
}
```

### Read-only collection exposure

```java
import java.util.List;

/**
 * Exposes roles as a read-only snapshot view.
 */
public List<String> roles() {
    return List.copyOf(roles);
}
```

### Checked vs unchecked exception rule

```java
import java.io.IOException;

/**
 * Loads a receipt, signaling recoverable I/O failure to the caller.
 */
public Receipt load(String id) throws IOException {
    return gateway.load(id);
}
```

### Generic wildcard guidance for public APIs

Use `? extends T` for input (producer) and `? super T` for output (consumer):

```java
import java.util.Collection;
import java.util.List;

/**
 * Runs every task in the given producer collection.
 */
public void processAll(Collection<? extends Task> tasks) {
    tasks.forEach(Task::run);
}

/**
 * Copies source strings into the given consumer list.
 */
public void addAll(List<? super String> target, List<String> source) {
    target.addAll(source);
}
```

### @FunctionalInterface for SAM types

```java
/**
 * Strategy for deciding whether a failed attempt should run again.
 */
@FunctionalInterface
public interface RetryStrategy {
    boolean shouldRetry(int attempt, Throwable lastFailure);
}
```

## Edge cases

- Questions about syntax availability across Java versions are outside this skill's scope.
  Use `java:java-language-syntax`.
- Questions about JUnit structure or test-first workflow are outside this skill's scope.
  Use `java:java-test`.
- Questions about performance tuning and concurrency model selection are outside this skill's scope.
  Use `java:java-performance-concurrency`.
- If the Java baseline does not support records (pre-16), fall back to `final` classes with manual equality and constructor validation.
- If a type owns evolving state or identity-bearing behavior, a record may not fit even when the baseline supports it.
- If the domain must remain extensible across package or module boundaries, prefer an open interface over a sealed hierarchy.

## Result

Explain the chosen type shape and material contract changes, with the Java baseline when relevant.
For implementation tasks, complete the authorized change and verify affected callers and behavior.
