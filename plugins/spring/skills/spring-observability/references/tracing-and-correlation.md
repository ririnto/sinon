---
title: Spring Tracing and Correlation Reference
description: >-
  Reference for trace propagation, baggage, MDC correlation, sampling, and OTLP export in Spring Boot.
---

Use this reference when the main question is how traces flow through a Spring app and how operators connect logs, spans, and downstream calls.

## Propagation Rule

Choose one propagation model deliberately and keep it aligned with the wider platform.

```yaml
management:
  tracing:
    propagation:
      type: W3C
```

## Log Correlation Rule

Include trace and span identifiers in the logging correlation pattern.

```yaml
logging:
  pattern:
    correlation: "[${spring.application.name:},%X{traceId:-},%X{spanId:-}] "
```

## Baggage Rule

Use baggage only for context that truly needs to move across service boundaries.

```yaml
management:
  tracing:
    baggage:
      remote-fields: x-request-id,x-user-id
      correlation:
        fields: x-request-id
```

Important note:

- baggage values can leak into logs and downstream services, so do not propagate sensitive or high-entropy values casually

## Sampling Rule

Treat sampling as an operator tradeoff, not a framework default to ignore.

```yaml
management:
  tracing:
    sampling:
      probability: 0.1
```

## OTLP Export Rule

Use explicit OTLP configuration when traces must flow into a collector.

```yaml
management:
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
```

## Async and Reactive Context Rule

If work crosses `@Async` or reactive boundaries, make sure trace context is propagated intentionally instead of assuming thread-local state will survive the hop.

### `@Async` Propagation Recipe

Use a `TaskDecorator` to capture the current `Observation` context on the submitting thread and restore it on the worker thread. Register it on the executor that backs `@Async`.

```java
@Configuration
@EnableAsync
class AsyncConfig implements AsyncConfigurer {

    private final ObservationRegistry observationRegistry;

    AsyncConfig(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(runnable -> {
            Observation currentObservation = observationRegistry.getCurrentObservation();
            if (currentObservation == null) {
                return runnable;
            }
            Observation.Scope scope = currentObservation.openScope();
            return () -> {
                try (scope) {
                    runnable.run();
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
```

This keeps the trace context alive across the thread hop without coupling business logic to tracing APIs. If Micrometer Tracing's `ContextPropagation` support is active on the classpath, it may handle this automatically; verify with an integration test before relying on the implicit path.

## Common Mistakes

- expecting trace context to survive arbitrary async boundaries automatically
- correlating logs without including trace or span identifiers in the pattern
- setting sampling to `1.0` everywhere without volume or cost justification
