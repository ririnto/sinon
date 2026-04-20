---
title: "Advanced StepVerifier"
description: "Open this when basic StepVerifier flow is not enough and you need scenario options, context expectations, or richer post-verification assertions."
---

Open this when the test is still primarily a `StepVerifier` scenario, but the ordinary path is no longer expressive enough.

## Scenario options

```java
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.test.StepVerifierOptions;

class ScenarioOptionsTest {
    @Test
    void namesScenarioAndControlsRequest() {
        StepVerifierOptions options = StepVerifierOptions.create()
            .scenarioName("range verification")
            .initialRequest(2);

        StepVerifier.create(Flux.range(1, 5), options)
            .expectNext(1, 2)
            .thenRequest(3)
            .expectNext(3, 4, 5)
            .verifyComplete();
    }
}
```

## Context expectations

```java
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.StepVerifierOptions;
import reactor.util.context.Context;

class ContextExpectationTest {
    @Test
    void verifiesAccessibleContext() {
        StepVerifier.create(
                Mono.deferContextual(context -> Mono.just(context.get("requestId"))),
                StepVerifierOptions.create().withInitialContext(Context.of("requestId", "req-1"))
            )
            .expectAccessibleContext()
            .contains("requestId", "req-1")
            .then()
            .expectNext("req-1")
            .verifyComplete();
    }
}
```

## Advanced post-verification assertions

```java
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class PostVerificationAssertionsTest {
    @Test
    void verifiesDroppedElements() {
        StepVerifier.create(Flux.just(1, 2, 3).filter(value -> value < 3))
            .expectNext(1, 2)
            .verifyThenAssertThat()
            .hasNotDroppedElements();
    }
}
```

## `assertNext(...)` for complex value inspection

When `expectNext(...)` equality is not enough, use `assertNext(...)` to run arbitrary assertions on each value.

```java
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;

class AssertNextAdvancedTest {
    @Test
    void inspectsMultipleValueProperties() {
        StepVerifier.create(Flux.just("first-value", "second-item"))
            .assertNext(v -> {
                assertThat(v).startsWith("first");
                assertThat(v).contains("-");
            })
            .assertNext(v -> {
                assertThat(v).startsWith("second");
                assertThat(v).endsWith("item");
            })
            .verifyComplete();
    }
}
```

## `consumeRecordedWith(...)` for full signal inspection

After verification, `consumeRecordedWith(...)` gives access to the complete list of recorded signals (onNext, onError, onComplete) for complex post-hoc assertions.

```java
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;

class RecordedSignalsTest {
    @Test
    void inspectsAllRecordedSignals() {
        StepVerifier.create(Flux.just("a", "b"))
            .expectNext("a", "b")
            .verifyThenAssertThat()
            .consumeRecordedWith(signals -> {
                assertThat(signals).hasSize(3);
            });
    }
}
```

## Guardrails

- Use `StepVerifierOptions` only when scenario naming, initial request, or initial context is the real blocker.
- Use `expectAccessibleContext()` only when context behavior is the thing being tested.
- Keep `verifyThenAssertThat()` for post-execution conditions, not as a replacement for ordinary signal assertions.
