---
title: "Noncompliant TestPublishers"
description: "Open this when the blocker is a noncompliant TestPublisher or deliberate spec-edge behavior for operator testing."
---

Open this when ordinary `TestPublisher.create()` is not enough because the test must simulate spec-edge or intentionally invalid upstream behavior.

## Available `Violation` options

| Violation | What it allows |
| --- | --- |
| `REQUEST_OVERFLOW` | emit more elements than requested (tests operator backpressure handling) |
| `ALLOW_NULL` | emit null values (tests operator null tolerance) |
| `CLEANUP_ON_TERMINATE` | emit after onComplete/onError (tests operator cleanup robustness) |

Combine multiple violations with bitwise OR:

```java
import reactor.test.publisher.TestPublisher;

int violations = TestPublisher.Violation.REQUEST_OVERFLOW
    | TestPublisher.Violation.ALLOW_NULL;

TestPublisher<Integer> publisher = TestPublisher.createNoncompliant(violations);
```

## Request overflow example

```java
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

class NoncompliantPublisherTest {
    @Test
    void allowsRequestOverflowForOperatorTests() {
        TestPublisher<Integer> publisher = TestPublisher.createNoncompliant(TestPublisher.Violation.REQUEST_OVERFLOW);

        StepVerifier.create(publisher.flux(), 0)
            .then(() -> publisher.next(1))
            .expectNext(1)
            .thenCancel()
            .verify();
    }
}
```

## Guardrails

- Use noncompliant publishers only when testing operator robustness or Reactive Streams edge behavior.
- Keep these tests isolated from the ordinary path so invalid upstream behavior stays explicit.
- Each violation type tests a specific spec boundary -- choose only the violations your test scenario requires.
