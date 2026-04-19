# Listener metrics and Micrometer

Open this reference when the ordinary producer and consumer path in [SKILL.md](../SKILL.md) is not enough and the blocker is listener metrics or Micrometer wiring.

## Metrics blocker

**Problem:** the team cannot explain queue lag, redelivery spikes, or listener latency from production signals.

**Solution:** record listener latency, failure counts, and dead-letter outcomes as first-class metrics.

```java
long start = System.nanoTime();
try {
    process(event);
} finally {
    meterRegistry.timer("amqp.listener.duration", "queue", "orders").record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
}
```

## Decision points

| Situation | First choice |
| --- | --- |
| queue lag, redelivery spikes, or listener latency are not visible | record listener metrics before changing concurrency or retry policy |

## Pitfalls

- Do not merge broker-connectivity failures and business-handling failures into one generic error metric.
- Keep queue or listener identifiers on metrics so hot paths are visible.
