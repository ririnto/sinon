# Distributed tracing for AMQP

Open this reference when the ordinary producer and consumer path in [SKILL.md](../SKILL.md) is not enough and the blocker is correlating publish and consume traces.

## Observation blocker

**Problem:** the application needs end-to-end traces for publish and consume paths.

**Solution:** enable observations and keep queue, exchange, and routing-key identifiers visible in telemetry.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-brave</artifactId>
    </dependency>
</dependencies>
```

- Correlate publish latency with listener handling latency.
- Distinguish conversion failures, transient downstream failures, and exhausted retries.

## Decision points

| Situation | First choice |
| --- | --- |
| publish and consume traces must correlate | enable tracing and keep messaging identifiers on spans |

## Pitfalls

- Do not log full payloads when message bodies may contain sensitive data.
- Keep trace identifiers and messaging identifiers correlated at the messaging seam.
