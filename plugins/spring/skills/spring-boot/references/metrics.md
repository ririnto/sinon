# Spring Boot metrics

Open this reference when the task is about metrics exports.

## Add the Prometheus registry when exposing `/actuator/prometheus`

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```kotlin
dependencies {
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}
```

## Expose the Prometheus endpoint

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
```

Expose `metrics` separately only when the task also needs the `/actuator/metrics` endpoint.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: metrics,prometheus
```

## Record a metric

```java
meterRegistry.counter("catalog.requests", "endpoint", "greetings").increment();
```

## Use timers for durations

```java
Timer.Sample sample = Timer.start(meterRegistry);
try {
    catalogService.search(region);
} finally {
    sample.stop(Timer.builder("catalog.search").tag("region", catalogProperties.region()).register(meterRegistry));
}
```

Prefer stable tags and avoid cardinality explosions. Use `uri` sparingly on high-cardinality paths; prefer fixed path templates like `/api/greetings/{id}` rather than concrete values like `/api/greetings/42`.

## Validation rule

Check the exported metric names and tag sets against the real dashboard or scrape target.
