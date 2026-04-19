---
title: "Noncompliant TestPublishers"
description: "Open this when the blocker is a noncompliant TestPublisher or deliberate spec-edge behavior for operator testing."
---

Open this when ordinary `TestPublisher.create()` is not enough because the test must simulate spec-edge or intentionally invalid upstream behavior.

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
