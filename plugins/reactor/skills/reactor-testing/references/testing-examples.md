# Reactor Testing Examples

## Ordinary `StepVerifier` success path

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

## Virtual time for delayed publishers

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

## `TestPublisher` for manual upstream control

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

## `PublisherProbe` for fallback path verification

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

## Request and cancellation choreography

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

## Post-verification assertions

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
            .expectComplete()
            .verifyThenAssertThat()
            .tookLessThan(Duration.ofSeconds(1));
    }
}
```

## `assertNext(...)` for complex value assertions

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

`assertNext(...)` receives the value and allows arbitrary assertions inside the lambda.
Use it when `expectNext(...)` equality matching is not expressive enough.

## Virtual time and `delayElement` caveat

`withVirtualTime` replaces Reactor's scheduler factory before the supplier runs, so default-timed operators such as `delayElement(Duration)` are compatible when the publisher is created lazily inside the supplier.
The failure mode is creating the delayed publisher before `withVirtualTime(...)`, or passing an explicit real scheduler to a timed operator.

```java
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
class DelayElementVirtualTimeTest {
    @Test
    void badEagerDelayElementInVirtualTime() {
        Mono<String> delayed = Mono.just("value").delayElement(Duration.ofSeconds(5));
        StepVerifier.withVirtualTime(() ->
            delayed
        )
            .expectSubscription()
            .expectNoEvent(Duration.ofSeconds(5))
            .expectNext("value")
            .verifyComplete();
    }
    @Test
    void goodLazyDelayElementInVirtualTime() {
        StepVerifier.withVirtualTime(() ->
            Mono.just("value").delayElement(Duration.ofSeconds(5))
        )
            .expectSubscription()
            .expectNoEvent(Duration.ofSeconds(5))
            .expectNext("value")
            .verifyComplete();
    }
}
```

The first test may hang or fail because the scheduler was captured before virtual time was installed.
The second test creates the `delayElement` publisher inside the supplier, allowing virtual time to replace the default timed scheduler.
