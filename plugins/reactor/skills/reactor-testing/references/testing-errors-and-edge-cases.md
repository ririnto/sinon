---
title: "Testing Errors and Edge Cases"
description: "Open this when the test involves error assertion patterns, error matching predicates, virtual-time failure modes, or verification method selection edge cases."
---

Open this when the ordinary `expectError(Class)` path is not enough and you need richer error assertions, virtual-time failure scenarios, or verification-method decision logic.

## Error assertion patterns

### Type-based error assertion

```java
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ErrorTypeTest {
    @Test
    void verifiesErrorType() {
        StepVerifier.create(Mono.error(new IllegalStateException("boom")))
            .expectError(IllegalStateException.class)
            .verify();
    }
}
```

### Predicate-based error matching

Use `expectErrorMatches` when the error condition is more specific than type alone.

```java
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ErrorPredicateTest {
    @Test
    void matchesErrorMessage() {
        StepVerifier.create(Mono.error(new RuntimeException("connection refused")))
            .expectErrorMatches(error -> error instanceof RuntimeException
                && "connection refused".equals(error.getMessage()))
            .verify();
    }
}
```

### Error consumption with assertions

Use `expectErrorSatisfies` when you need to assert multiple properties on the error object.

```java
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;

class ErrorSatisfiesTest {
    @Test
    void assertsMultipleErrorProperties() {
        StepVerifier.create(Mono.error(new IllegalArgumentException("bad arg", new RuntimeException("cause"))))
            .consumeErrorWith(error -> {
                assertThat(error).isInstanceOf(IllegalArgumentException.class);
                assertThat(error.getMessage()).isEqualTo("bad arg");
                assertThat(error.getCause()).isInstanceOf(RuntimeException.class);
            })
            .verify();
    }
}
```

## `verifyComplete()` vs `verifyError()` vs `verify()` decision

| Scenario | Method | Notes |
| --- | --- | --- |
| Publisher completes with values | `verifyComplete()` | Also asserts no error follows |
| Publisher terminates with an error | `verifyError(Class)` | Shortcut for `expectError(Class).verify()` |
| Publisher may complete or error | `verify()` | Use after explicit expectComplete or expectError* |
| Error with custom predicate | `expectErrorMatches(predicate).verify()` | Full control over matching |

## Timeout behavior in virtual time

```java
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RetryAndTimeoutTest {
    @Test
    void verifiesTimeout() {
        StepVerifier.withVirtualTime(() -> Mono.delay(Duration.ofSeconds(10)))
            .expectSubscription()
            .expectNoEvent(Duration.ofSeconds(5))
            .expectTimeout(Duration.ofSeconds(5))
            .verify();
    }
}
```

`expectTimeout(duration)` asserts that no signal arrives within the specified window. The publisher under test may continue running after the timeout -- this only checks that the verifier's expectation window expired without a matching signal.

## Virtual-time failure modes

### Scheduler not advancing

If a delayed publisher inside `withVirtualTime` never emits, check that:

- The publisher is created inside the supplier lambda, not before.
- The delay uses `VirtualTimeScheduler` (automatic inside `withVirtualTime`) rather than a real scheduler.
- No `subscribeOn(Schedulers.parallel())` or similar real-scheduler call bypasses virtual time.

```java
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class VirtualTimeSchedulerBypassTest {
    // BAD: real scheduler bypasses virtual time
    @Test
    void badRealSchedulerInVirtualTime() {
        StepVerifier.withVirtualTime(() ->
            Mono.delay(Duration.ofSeconds(5))
                .subscribeOn(Schedulers.parallel()) // breaks virtual time
        )
            .expectSubscription()
            .expectNoEvent(Duration.ofSeconds(5))
            .expectNext(0L)
            .verifyComplete(); // may hang or fail
    }

    // GOOD: no real scheduler inside virtual time block
    @Test
    void goodPureVirtualTime() {
        StepVerifier.withVirtualTime(() -> Mono.delay(Duration.ofSeconds(5)))
            .expectSubscription()
            .expectNoEvent(Duration.ofSeconds(5))
            .expectNext(0L)
            .verifyComplete();
    }
}
```

### `expectNoEvent` includes subscription

`expectNoEvent(duration)` treats the subscription event itself as an event. If the source defers subscription, the timer starts at subscription.

```java
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ExpectNoEventEdgeCase {
    @Test
    void subscriptionIsAnEvent() {
        StepVerifier.withVirtualTime(() -> Mono.defer(() ->
            Mono.just("value").delayElement(Duration.ofMillis(100))
        ))
            .expectSubscription()  // this counts as first event
            .expectNoEvent(Duration.ofMillis(50))  // only 50ms of silence AFTER subscription
            .expectNext("value")
            .verifyComplete();
    }
}
```

## Guardrails

- Keep timeout scenarios deterministic with virtual time where possible.
- Do not pull framework-specific clients or web-stack tests into this skill.
- Use `consumeErrorWith` when you need complex multi-assertion error checking; use `expectErrorMatches` for single-predicate cases.
- If the test needs to verify dropped elements or discarded signals alongside error behavior, combine error expectations with `verifyThenAssertThat()`.
