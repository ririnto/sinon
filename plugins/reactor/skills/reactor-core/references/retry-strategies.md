---
title: "Retry and Repeat Strategies"
description: "Open this when simple retry or repeat is not enough and you need backoff, filtering, retry exhaustion, repeat exhaustion, or explicit re-subscription policies."
---

Open this when failure recovery needs a deliberate retry policy rather than a plain `retry(n)`, or when completion-side re-subscription needs a termination condition.

## Error-side retry: choose the policy

| Need | Use | Typical fit |
| --- | --- | --- |
| fixed attempt count | `retry(n)` | small transient failures with no timing policy |
| filtered retry rules | `Retry.max(n).filter(...)` | retry only known transient exceptions |
| fixed delay | `Retry.fixedDelay(n, delay)` | predictable spacing |
| exponential backoff | `Retry.backoff(n, minDelay)` | remote systems and outage recovery |
| inspect each retry signal | `doBeforeRetry(...)` / `doAfterRetry(...)` | auditing or metrics |

## Bounded backoff (error retry)

```java
import java.time.Duration;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
final class RetriedLookup {
    Mono<String> fetch() {
        return callRemote()
            .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                .filter(IllegalStateException.class::isInstance)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }
    private Mono<String> callRemote() {
        return Mono.error(new IllegalStateException("temporary"));
    }
}
```

## Completion-side repeat

`repeat` re-subscribes when the publisher completes successfully (not on error). Use it for polling, retryable idempotent operations, or any completion-loop pattern.

### Fixed-count repeat

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
final class FixedRepeat {
    Flux<String> pollThreeTimes() {
        return fetchStatus()
            .repeat(3);
    }
    private Mono<String> fetchStatus() {
        return Mono.just("PENDING");
    }
}
```

### Repeat with termination condition

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
final class ConditionalRepeat {
    Flux<String> pollUntilDone() {
        return fetchStatus()
            .repeat()
            .takeUntil("DONE"::equals)
            .take(10);
    }
    private Mono<String> fetchStatus() {
        return Mono.just("PENDING");
    }
}
```

### Repeat with backoff schedule

```java
import java.time.Duration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
final class RepeatedWithBackoff {
    Flux<String> scheduledPoll() {
        return fetchStatus()
            .repeatWhen(completed -> completed.delayElements(Duration.ofSeconds(2)))
            .take(20);
    }
    private Mono<String> fetchStatus() {
        return Mono.just("PENDING");
    }
}
```

## Retry vs repeat decision

| Trigger | Operator | Re-subscribes on... |
| --- | --- | --- |
| error signal | `retry*` | `onError` |
| complete signal | `repeat*` | `onComplete` |

Always combine `repeat*` with a termination guard (`take(n)`, `repeatUntil(...)`, `repeatWhen(...)`) to prevent unbounded loops.

## Guardrails

- Do not retry validation failures or permanent domain errors.
- Backoff without a bound still turns an outage into pressure.
- Make retry and repeat exhaustion behavior explicit so the final error is predictable.
- Keep side effects idempotent before adding retries.
- Do not use `repeat()` without a termination condition -- it loops forever on every completion.
