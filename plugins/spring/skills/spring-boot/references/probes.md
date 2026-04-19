# Spring Boot probes

Open this reference when the task is about liveness or readiness probe behavior.

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
```

Keep liveness and readiness semantics aligned with the actual deployment platform.

## Gotchas

- Do not treat readiness failure as the same thing as process-death liveness failure.
