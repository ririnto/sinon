---
title: "Testing Timeouts"
description: "Open this when the blocker is timeout behavior or a virtual-time failure case."
---

Open this when the ordinary test flow is correct but the blocker is timeout behavior or a virtual-time failure case.

## Timeout behavior

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
            .expectTimeout(Duration.ofSeconds(5))
            .verify();
    }
}
```

## Guardrails

- Keep timeout scenarios deterministic with virtual time where possible.
- Do not pull framework-specific clients or web-stack tests into this skill.
