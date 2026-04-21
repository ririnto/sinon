# Spring Boot probes

Open this reference when the task is about liveness or readiness probe behavior.

Boot enables liveness and readiness probes by default. In Kubernetes, map those endpoints into platform probes, and set `management.endpoint.health.probes.enabled` only when you need to override that default explicitly.

## Kubernetes probe mapping

When probes are enabled, expose `liveness` and `readiness` paths explicitly in the Kubernetes manifest:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  periodSeconds: 30
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  periodSeconds: 10
  failureThreshold: 3
```

Point the probe at the port that actually serves Actuator. If `management.server.port` is separate, use that management port or expose the additional probe paths on the main application port with `management.endpoint.health.probes.add-additional-paths=true`.

Keep liveness and readiness semantics aligned with the actual deployment platform.

## Gotchas

- Do not treat readiness failure as the same thing as process-death liveness failure.
- Liveness failure means the platform should restart the application or container. Readiness failure means the application is live but should stop receiving traffic.
