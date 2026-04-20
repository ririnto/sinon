---
name: java-language-design
description: >-
  Design idiomatic Java APIs, review class structure for immutability and clarity,
  choose between records and sealed classes, decide checked vs unchecked exception boundaries,
  and shape public contracts with narrow surfaces and explicit value semantics.
  Use when the user asks to design a Java API, review Java class structure,
  refactor Java code, or needs guidance on idiomatic Java language and library design.
---

# Java Language Design

Produce idiomatic, maintainable Java code and API designs. The common case is choosing a clearer type shape, a narrower contract, and an unsurprising exception and mutability model before touching framework details.

## Operating rules

- SHOULD prefer value semantics where possible.
- MUST keep public APIs narrow and intention-revealing.
- MUST expose immutable views unless mutation is part of the contract.
- SHOULD prefer simple, explicit contracts over inheritance-heavy designs.
- SHOULD check whether records, sealed classes, enums, or interfaces fit the model better than ordinary classes.
- MUST use checked exceptions sparingly and only when callers can meaningfully recover.
- MUST keep constructors and factories explicit about invariants.
- MUST avoid leaking implementation types in public signatures.
- SHOULD order top-level class members as: static fields, instance fields, constructors, static methods, overridden methods, instance methods, then inner static classes/records/enums.
- SHOULD order members within each method group by visibility: `public`, `protected`, package-private, then `private`.

## Procedure

1. Read the target class and the nearest related tests or callers first.
2. Identify the Java baseline and whether the surface is internal or externally consumed.
3. Choose the smallest design change that improves clarity while preserving the requested behavior.
4. Make value semantics, exception contracts, and mutability boundaries explicit before adding extra abstraction.
5. Verify collection-returning methods do not leak internal mutable state.
6. Confirm checked exceptions remain only where callers can meaningfully recover.

## First runnable commands

Start from one explicit value carrier and one explicit capability interface:

```java
public record CustomerId(String value) {
}

public interface PaymentGateway {
    Receipt charge(ChargeRequest request);
}
```

Use when: tightening a contract or replacing a vague mutable DTO or service surface.

## Ready-to-adapt templates

### Factory for clearer invariants

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

### Builder for complex construction

When a type has many optional parameters and a factory method becomes unwieldy:

```java
public final class QueryOptions {
    private final int limit;
    private final int offset;
    private final String sortBy;
    private final boolean ascending;

    private QueryOptions(Builder builder) {
        this.limit = builder.limit;
        this.offset = builder.offset;
        this.sortBy = builder.sortBy;
        this.ascending = builder.ascending;
    }

    public int limit() {
        return limit;
    }

    public int offset() {
        return offset;
    }

    public String sortBy() {
        return sortBy;
    }

    public boolean ascending() {
        return ascending;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int limit = 100;
        private int offset = 0;
        private String sortBy = "id";
        private boolean ascending = true;

        public Builder limit(int limit) {
            if (limit < 1) {
                throw new IllegalArgumentException("limit must be positive");
            }
            this.limit = limit;
            return this;
        }

        public Builder offset(int offset) {
            if (offset < 0) {
                throw new IllegalArgumentException("offset must be >= 0");
            }
            this.offset = offset;
            return this;
        }

        public Builder sortBy(String sortBy) {
            this.sortBy = sortBy;
            return this;
        }

        public Builder ascending(boolean ascending) {
            this.ascending = ascending;
            return this;
        }

        public QueryOptions build() {
            return new QueryOptions(this);
        }
    }
}
```

### equals and hashCode for non-record value types (pre-Java 17)

```java
import java.util.Objects;

public final class Money {
    private final String currency;
    private final long cents;

    public Money(String currency, long cents) {
        this.currency = Objects.requireNonNull(currency);
        this.cents = cents;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money m
            && currency.equals(m.currency)
            && cents == m.cents;
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency, cents);
    }
}
```

### Member ordering baseline

```java
public final class Example {

    private static final String TYPE = "example";

    private final String value;

    public Example(String value) {
        this.value = value;
    }

    public static Example of(String value) {
        return new Example(value);
    }

    @Override
    public String toString() {
        return value;
    }

    public String value() {
        return value;
    }

    static final class Parser {
    }
}
```

### Read-only collection exposure

```java
import java.util.List;

public List<String> roles() {
    return List.copyOf(roles);
}
```

### Checked vs unchecked exception rule

```java
import java.io.IOException;

public Receipt load(String id) throws IOException {
    return gateway.load(id);
}
```

### Generic wildcard guidance for public APIs

Use `? extends T` for input (producer) and `? super T` for output (consumer):

```java
import java.util.Collection;
import java.util.List;

public void processAll(Collection<? extends Task> tasks) {
    tasks.forEach(Task::run);
}

public void addAll(List<? super String> target, List<String> source) {
    target.addAll(source);
}
```

### @FunctionalInterface for SAM types

```java
@FunctionalInterface
public interface RetryStrategy {
    boolean shouldRetry(int attempt, Throwable lastFailure);
}
```

## Edge cases

- If the question is about syntax availability across Java versions rather than design semantics, that is outside this skill's scope.
- If the question is about JUnit structure or test-first workflow, that is outside this skill's scope.
- If the question is about performance tuning or concurrency model selection, that is outside this skill's scope.
- If the Java baseline does not support records (pre-17), fall back to `final` classes with manual equality and constructor validation.
- If a type owns evolving state or identity-bearing behavior, a record may not fit even when the baseline supports it.
- If the domain must remain extensible across package or module boundaries, prefer an open interface over a sealed hierarchy.

## Output contract

Return:

1. The recommended type shape with Java baseline annotation.
2. Explicit mutability and visibility decisions.
3. Exception contract with recoverability rationale.
4. Member ordering that follows the declared baseline.

## Support-file pointers

| If the blocker is... | Open... |
| --- | --- |
| records, sealed classes, and semantic modeling tradeoffs once the Java baseline is known | [`language-features.md`](./references/language-features.md) |
| mutability, collection exposure, visibility, or exception-contract review | [`api-design.md`](./references/api-design.md) |

## Gotchas

- Do not return concrete mutable collections directly from public APIs.
- Do not use inheritance when the model is just data or capability.
- Do not throw checked exceptions for non-recoverable failures.
- Do not hide invariants inside overloaded constructors.
- Do not mix fields, constructors, methods, and nested types in scan-hostile order.
