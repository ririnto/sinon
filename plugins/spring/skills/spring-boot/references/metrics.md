# Spring Boot metrics

Open this reference when the task is about metrics exports.

```java
meterRegistry.counter("boot.requests", "uri", "/api/greetings").increment();
```

Prefer stable tags and avoid cardinality explosions.

## Validation rule

Check the exported metric names and tag sets against the real dashboard or scrape target.
