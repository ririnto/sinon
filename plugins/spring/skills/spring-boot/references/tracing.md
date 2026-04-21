# Spring Boot tracing

Open this reference when the task is about tracing exports.

Keep one tracing export path explicit and verify propagation behavior before widening instrumentation.

## Choose one tracing stack

- Use `org.springframework.boot:spring-boot-starter-opentelemetry` for OpenTelemetry with OTLP export.
- Use `org.springframework.boot:spring-boot-starter-zipkin` for Zipkin export.

## OTLP export config

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: https://collector:4318/v1/traces
```

## Zipkin config

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
    export:
      zipkin:
        endpoint: https://zipkin:9411/api/v2/spans
```

## Gotchas

- Do not enable tracing broadly without checking sampling and propagation expectations.
- Set the export endpoint before enabling tracing on production networks; local collector defaults rarely match deployment.
