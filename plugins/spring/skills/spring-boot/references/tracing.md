# Spring Boot tracing

Open this reference when the task is about tracing exports, OTel SDK configuration, OTLP SSL bundles, metrics compression, truststore certificate metrics, or exemplar filtering.

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

## Disable OTel SDK

Disable the OTel SDK while keeping propagators configured.
No-op implementations replace `SdkTracerProvider`, `SdkLoggerProvider`, and `SdkMeterProvider`.

```yaml
management:
  opentelemetry:
    enabled: false
```

## OTel sampler selection

```yaml
management:
  opentelemetry:
    tracing:
      sampler: always_on
```

Supported sampler values: `always_on`, `always_off`, `parent_based`, `ratio_based`.
Use `ratio_based` for probabilistic sampling.

## OTLP SSL bundle support

OTLP exporters for traces, metrics, and logs support SSL bundles.

```yaml
management:
  opentelemetry:
    tracing:
      export:
        otlp:
          ssl:
            bundle: example
    logging:
      export:
        otlp:
          ssl:
            bundle: example
  otlp:
    metrics:
      export:
        ssl:
          bundle: example
```

## OTLP metrics compression

Enable gzip compression for metrics exported over OTLP.

```yaml
management:
  otlp:
    metrics:
      export:
        compression-mode: gzip
```

## Truststore certificate expiry metrics

Expiry times from certificates in the truststore are exposed as metrics automatically.
No additional configuration is required when an SSL bundle with a truststore is configured.

## OTel SDK environment variables

Boot reads most OpenTelemetry SDK environment variables (e.g.
`OTEL_SERVICE_NAME`, `OTEL_RESOURCE_ATTRIBUTES`, `OTEL_EXPORTER_OTLP_ENDPOINT`) and applies them to the auto-configured SDK.

## Exemplar filtering

Fine-grained control over which spans produce exemplars.

```yaml
management:
  tracing:
    exemplars:
      filter: trace-id
```

## Gotchas

- Do not enable tracing broadly without checking sampling and propagation expectations.
- Set the export endpoint before enabling tracing on production networks.
  - Local collector defaults rarely match deployment.
- When disabling the OTel SDK, propagators remain active so context can still flow through the system.
