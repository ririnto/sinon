---
name: reactor-testing
description: >-
  Test Reactor publishers with reactor-test using StepVerifier, virtual time, TestPublisher, and PublisherProbe. Use this skill when designing or reviewing Reactor tests with reactor-test: StepVerifier workflow, virtual time, request and cancellation assertions, TestPublisher, PublisherProbe, and ordinary post-verification checks.
metadata:
  title: "Reactor Testing"
  official_project_url: "https://projectreactor.io/docs/test/release/reference/"
  reference_doc_urls:
    - "https://projectreactor.io/docs/test/release/reference/"
    - "https://projectreactor.io/docs/test/release/api/"
  version: "3.7"
  dependencies:
    - "io.projectreactor:reactor-core:3.7.x"
    - "io.projectreactor:reactor-test:3.7.x"
---

Test Reactor publishers with the ordinary `reactor-test` path.

This skill covers everyday `StepVerifier` flow, success/empty/error assertions, virtual time, request and cancellation assertions, `TestPublisher`, `PublisherProbe`, and ordinary post-verification checks. Keep advanced `StepVerifierOptions`, context-specific expectations, timeout-heavy scenarios, and noncompliant publishers in blocker references.

## Use this skill when

- testing a `Flux` or `Mono` with `StepVerifier`
- verifying value order, completion, emptiness, or errors
- testing time-based operators without waiting for real time
- controlling upstream emission manually with `TestPublisher`
- checking whether a fallback or alternate branch was actually subscribed with `PublisherProbe`
- asserting request, cancellation, or dropped/discarded signal behavior after verification

## Do not use this skill for

- ordinary `Flux` / `Mono` operator design as the main problem
- scheduler tuning or thread placement as the main problem
- framework-specific test clients such as Spring `WebTestClient`
- benchmarking or performance profiling
- transport-level or web-stack integration testing

## Coverage map

| reactor-test surface | Keep in this file | Open a reference when... |
| --- | --- | --- |
| ordinary `StepVerifier` workflow | create, expect signals, verify | scenario options or complex choreography become the blocker |
| success, empty, and error assertions | `expectNext`, `expectComplete`, `expectError*` | custom predicates or richer assertion flows dominate the test |
| virtual time | `withVirtualTime(...)`, `expectNoEvent(...)`, `thenAwait(...)` | scheduler caveats or complex timing behavior become the blocker |
| request and cancellation | initial request, `thenRequest(...)`, `thenCancel()` | the test is mainly about request choreography |
| `TestPublisher` | manual emission for downstream testing | spec-violation or noncompliant publisher behavior becomes the blocker |
| `PublisherProbe` | verify subscription path without real data exchange | probe wrapping and advanced branch checks dominate the test |
| post-execution assertions | `verifyThenAssertThat()` for dropped/discarded checks | hook-heavy or context-specific assertions become the blocker |
| context test boundary | recognize context assertions as part of advanced verification | context expectations become the main job |

## Operating rules

- Start with `StepVerifier.create(...)` unless time control is required.
- Use `withVirtualTime(...)` for delayed or interval-based publishers.
- Build the publisher lazily inside the virtual-time supplier.
- Make completion, emptiness, and error behavior explicit in the verifier.
- Use `TestPublisher` when the test must control upstream signals directly.
- Use `PublisherProbe` when the test is really about which branch was subscribed.
- Use `verifyThenAssertThat()` when dropped or discarded signals matter after execution.
- Keep framework-specific test clients out of this skill.

## Decision path

1. Choose the test tool.
   - Ordinary publisher verification: `StepVerifier`.
   - Manual upstream control: `TestPublisher`.
   - Branch/subscription-path verification: `PublisherProbe`.
2. Choose the timing model.
   - No timers: `StepVerifier.create(...)`.
   - Time-based operators: `StepVerifier.withVirtualTime(...)`.
3. Choose the expectation type.
   - Values: `expectNext(...)`, `expectNextCount(...)`, `assertNext(...)`.
   - Empty completion: `expectComplete()`.
   - Error: `expectError*`.
   - Request/cancel: initial request, `thenRequest(...)`, `thenCancel()`.
4. Add post-verification checks if dropped or discarded elements matter.
5. Open a reference only when the blocker is advanced verifier configuration or unusual edge-case behavior.

## Ordinary workflow

1. State whether the test is about values, timing, request flow, manual upstream control, or execution path.
2. Create the publisher under test.
3. Build the verifier with the correct timing model.
4. Encode the expected signals in order.
5. Trigger verification.
6. Add post-verification assertions only if the scenario needs them.

## reactor-test quick reference

| Need | Default move | Why |
| --- | --- | --- |
| basic publisher verification | `StepVerifier.create(publisher)` | ordinary signal assertions |
| empty completion | `expectComplete()` | asserts no further signals |
| specific error type | `expectError(Class)` | keeps error intent explicit |
| complex value assertion | `assertNext(consumer)` | multi-property inspection per value |
| time-based operator | `StepVerifier.withVirtualTime(() -> publisher)` | avoids real delays |
| no events during a window | `expectSubscription().expectNoEvent(duration)` | subscription itself is an event |
| additional demand mid-test | `thenRequest(n)` | controls request flow |
| manual upstream source | `TestPublisher.create()` | emits signals on demand |
| branch selection check | `PublisherProbe.empty()` or `PublisherProbe.of(...)` | verifies subscription path |
| dropped/discarded checks | `verifyThenAssertThat()` | post-execution assertions |
| recorded-signal inspection | `verifyThenAssertThat().consumeRecordedWith(...)` | inspect all recorded signals after verification |

## Ready-to-adapt examples

### Ordinary `StepVerifier` success path

```java
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class BasicStepVerifierTest {
    @Test
    void verifiesValuesAndCompletion() {
        Flux<String> flux = Flux.just("alpha", "beta", "gamma");

        StepVerifier.create(flux)
            .expectNext("alpha", "beta", "gamma")
            .verifyComplete();
    }
}
```

### Virtual time for delayed publishers

```java
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class VirtualTimeTest {
    @Test
    void verifiesDelayedSignalWithoutWaiting() {
        StepVerifier.withVirtualTime(() -> Mono.delay(Duration.ofSeconds(5)))
            .expectSubscription()
            .expectNoEvent(Duration.ofSeconds(5))
            .expectNext(0L)
            .verifyComplete();
    }
}
```

### `TestPublisher` for manual upstream control

```java
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

class TestPublisherExample {
    @Test
    void emitsOnDemand() {
        TestPublisher<String> publisher = TestPublisher.create();

        StepVerifier.create(publisher.flux())
            .then(() -> publisher.emit("first", "second"))
            .expectNext("first", "second")
            .verifyComplete();
    }
}
```

### `PublisherProbe` for fallback path verification

```java
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.PublisherProbe;

class PublisherProbeExample {
    @Test
    void verifiesFallbackSubscription() {
        PublisherProbe<String> fallback = PublisherProbe.empty();
        Mono<String> result = Mono.<String>empty().switchIfEmpty(fallback.mono());

        StepVerifier.create(result)
            .verifyComplete();

        fallback.assertWasSubscribed();
    }
}
```

### Request and cancellation choreography

```java
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class RequestAndCancellationExample {
    @Test
    void controlsDemandExplicitly() {
        StepVerifier.create(Flux.range(1, 10), 1)
            .expectNext(1)
            .thenRequest(2)
            .expectNext(2, 3)
            .thenCancel()
            .verify();
    }
}
```

### Post-verification assertions

```java
import org.junit.jupiter.api.Test;
import java.time.Duration;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class PostVerificationExample {
    @Test
    void checksExecutionTime() {
        StepVerifier.create(Flux.just("a", "b"))
            .expectNext("a", "b")
            .verifyThenAssertThat()
            .tookLessThan(Duration.ofSeconds(1));
    }
}
```

### `assertNext(...)` for complex value assertions

Use `assertNext(...)` when a value needs multi-assertion inspection beyond simple equality.

```java
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;

class AssertNextExample {
    @Test
    void assertsMultiplePropertiesPerValue() {
        StepVerifier.create(Flux.just("alpha", "beta", "gamma"))
            .expectNext("alpha")
            .assertNext(value -> {
                assertThat(value).hasSizeGreaterThan(1);
                assertThat(value).startsWith("b");
            })
            .expectNext("gamma")
            .verifyComplete();
    }
}
```

`assertNext(...)` receives the value and allows arbitrary assertions inside the lambda. Use it when `expectNext(...)` equality matching is not expressive enough.

### Virtual time and `delayElement` caveat

`delayElement` uses `Schedulers.parallel()` by default, which bypasses virtual time just like `subscribeOn`. Use `delaySubscription` instead when working inside `withVirtualTime`, or ensure no real-scheduler call appears in the publisher chain.

```java
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DelayElementVirtualTimeTest {
    @Test
    void badDelayElementInVirtualTime() {
        StepVerifier.withVirtualTime(() ->
            Mono.just("value").delayElement(Duration.ofSeconds(5))
        )
            .expectSubscription()
            .expectNoEvent(Duration.ofSeconds(5))
            .expectNext("value")
            .verifyComplete();
    }
    @Test
    void goodVirtualTimeWithoutDelayElement() {
        StepVerifier.withVirtualTime(() ->
            Mono.just("value").delaySubscription(Duration.ofSeconds(5))
        )
            .expectSubscription()
            .expectNoEvent(Duration.ofSeconds(5))
            .expectNext("value")
            .verifyComplete();
    }
}
```

The first test may hang or fail because `delayElement` uses the real `parallel()` scheduler. The second test uses `delaySubscription` which is compatible with virtual time.

## Common pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| forgetting to call `verify()` or a shortcut | the scenario never executes | end every verifier chain with `verify*` |
| building the virtual-time publisher outside the supplier | timers are created too early | create the publisher inside `withVirtualTime(...)` |
| using real time for delayed publishers | tests become slow or flaky | use virtual time |
| using `StepVerifier` when the real question is branch selection | signal assertions miss subscription-path intent | use `PublisherProbe` |
| asserting complicated upstream timing without control | behavior stays nondeterministic | use `TestPublisher` |

## Validation checklist

- [ ] The ordinary path covers `StepVerifier`, virtual time, `TestPublisher`, and `PublisherProbe`.
- [ ] Success, empty, and error expectations are explicit.
- [ ] Virtual time is shown with a lazy supplier.
- [ ] Request and cancellation assertions are described in the ordinary path.
- [ ] Post-verification assertions are available without opening a reference.
- [ ] Advanced verifier options and edge cases are routed to references.
- [ ] The ordinary path is understandable from this file alone.

## References

| Open this when... | Reference |
| --- | --- |
| basic verifier flow is not enough and you need scenario options, context expectations, or richer post-verification assertions | [Advanced StepVerifier](references/stepverifier-advanced.md) |
| the blocker is timeout behavior or virtual-time failure cases | [Testing Errors and Edge Cases](references/testing-errors-and-edge-cases.md) |
| the blocker is a noncompliant `TestPublisher` or deliberate spec-edge behavior | [Noncompliant TestPublishers](references/noncompliant-testpublisher.md) |

## Output contract

Return:

1. The chosen reactor-test tool and why it matches the test intent.
2. The signal, timing, request, or branch expectations encoded in the verifier.
3. Any post-verification assertion that matters after execution.
4. Any blocker that requires opening exactly one reference.
