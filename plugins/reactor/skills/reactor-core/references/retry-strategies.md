---
title: "Retry Strategies"
description: "Open this when simple retry is not enough and you need retryWhen, backoff, filtering, or explicit retry exhaustion behavior."
---

Open this when failure recovery needs a deliberate retry policy rather than a plain `retry(n)`.

## Choose the policy

| Need | Use | Typical fit |
| --- | --- | --- |
| fixed attempt count | `retry(n)` | small transient failures with no timing policy |
| filtered retry rules | `Retry.max(n).filter(...)` | retry only known transient exceptions |
| fixed delay | `Retry.fixedDelay(n, delay)` | predictable spacing |
| exponential backoff | `Retry.backoff(n, minDelay)` | remote systems and outage recovery |
| inspect each retry signal | `doBeforeRetry(...)` / `doAfterRetry(...)` | auditing or metrics |

## Bounded backoff

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

## Guardrails

- Do not retry validation failures or permanent domain errors.
- Backoff without a bound still turns an outage into pressure.
- Make retry exhaustion behavior explicit so the final error is predictable.
- Keep side effects idempotent before adding retries.
