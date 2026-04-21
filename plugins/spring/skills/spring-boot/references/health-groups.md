# Spring Boot health groups

Open this reference when the task is about health groups.

Use health groups when different audiences need different readiness signals.

## Define a health group

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: readinessState,db
          exclude: livenessState
```

## Map a group to a Kubernetes probe

```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  periodSeconds: 10
  failureThreshold: 3
```

Use the port that actually serves Actuator. If `management.server.port` is separate from the main server port, point the probe at that management port or expose probe paths on the main port explicitly.

## Validation rule

Verify each group exposes only the indicators the intended audience should see.
