---
title: Java API Design Reference
description: >-
  Reference for Java API design heuristics, public contracts,
  mutability boundaries, visibility choices, collection exposure,
  and exception-contract review rules.
---

Open this reference when reviewing or proposing a Java public contract and the blocker is no longer syntax, but the exact API shape, mutability boundary, visibility choice, or exception contract. This file should be sufficient on its own to finish that review.

Use this file to finish one of these jobs:

- check whether a returned type leaks mutability or implementation details
- decide whether a named factory is clearer than overloaded constructors
- decide whether absence should be modeled with `Optional<T>`, `null`, or a stricter invariant
- review checked vs unchecked exception boundaries
- decide whether a type should stay public, package-private, or hidden behind an interface

## Core heuristics

- Prefer interfaces for capabilities and records for simple value carriers.
- Prefer explicit factory methods when invariants or naming would be unclear in overloaded constructors.
- Return interfaces such as `List` or `Map` rather than concrete collection implementations.
- Document nullability assumptions in the absence of a project-level annotation standard.
- Keep exception contracts stable and unsurprising.

## Specific review points

- Mutability of returned state
- Exposure of internal collections
- Equality and hash code semantics
- Serialization boundaries
- Package-private vs public visibility
- Checked vs unchecked exception choices

## Broken vs correct review patterns

Mutable collection leak:

```java
import java.util.ArrayList;
import java.util.List;

// Broken: returns mutable internal list
final class UserDirectory {
    private final ArrayList<User> users = new ArrayList<>();

    public ArrayList<User> users() {
        return users;
    }
}
```

```java
import java.util.ArrayList;
import java.util.List;

// Correct: returns immutable copy with interface type
final class UserDirectory {
    private final ArrayList<User> users = new ArrayList<>();

    public List<User> users() {
        return List.copyOf(users);
    }
}
```

Use when: reviewing whether a public getter leaks mutability or overcommits callers to an implementation type.

Ambiguous absence contract:

```java
// Broken: callers must guess whether null is possible
interface SessionRepository {
    Session findByToken(String token);
}
```

```java
import java.util.Optional;

// Correct: absence is explicit in the return type
interface SessionRepository {
    Optional<Session> findByToken(String token);
}
```

Use when: callers need to distinguish ordinary absence from an error instead of guessing whether `null` is part of the contract.

Named factory for invariant-heavy construction:

```java
// Broken: constructor hides the meaning of parameters and invariants
final class PortRange {
    private final int lo;
    private final int hi;

    public PortRange(int lo, int hi) {
        if (lo > hi) {
            throw new IllegalArgumentException("lo must be <= hi");
        }
        this.lo = lo;
        this.hi = hi;
    }
}
```

```java
// Correct: named factory makes the invariant and units visible at the call site
final class PortRange {
    private final int lo;
    private final int hi;

    private PortRange(int lo, int hi) {
        this.lo = lo;
        this.hi = hi;
    }

    public static PortRange ofInclusive(int lo, int hi) {
        if (lo > hi) {
            throw new IllegalArgumentException("lo must be <= hi");
        }
        return new PortRange(lo, hi);
    }
}
```

Use when: constructor overloads would hide invariants, units, or naming that matter at the call site.

Recoverable vs programming-error exceptions:

```java
// Broken: no distinction between recoverable and programming errors
interface DocumentStore {
    Document load(DocumentId id);
}
```

```java
import java.io.IOException;

// Correct: checked exception for recoverable I/O failure, unchecked for programming error
interface DocumentStore {
    Document load(DocumentId id) throws IOException;
}

final class DocumentParser {
    public Document parse(String raw) {
        if (raw.isBlank()) {
            throw new IllegalArgumentException("raw must not be blank");
        }
        return parseInternal(raw);
    }
}
```

Use when: deciding whether the caller can reasonably recover from a boundary failure or whether the problem is a programming error in local input.

Capability interface with hidden implementation:

```java
// Broken: callers depend on concrete class
public final class EmailValidator {
    public boolean isValid(String value) {
        return value.contains("@");
    }
}
```

```java
// Correct: callers depend on capability, implementation stays hidden
public interface EmailValidationService {
    boolean isValid(String value);
}

final class DefaultEmailValidationService implements EmailValidationService {
    @Override
    public boolean isValid(String value) {
        return value.contains("@");
    }
}
```

Use when: callers depend on a capability while implementation choice, construction, or replacement should remain internal to the package or module.

## Review checklist

- Does the public signature expose an implementation type or mutable backing state?
- Does the contract make absence explicit, or does it force callers to infer a `null` convention?
- Would a named factory make construction rules or units more obvious than constructors?
- Are checked exceptions limited to failures the caller can actually recover from?
- Could the public API depend on an interface while the implementation stays package-private?
