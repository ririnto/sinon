# Spring Boot sanitization

Open this reference when the blocker is sanitizing sensitive Actuator values.

Keep access policy and sanitization explicit rather than relying on defaults you have not reviewed.

```yaml
management:
  endpoint:
    env:
      show-values: never
    configprops:
      show-values: never
```

Start with masked values and widen access only after reviewing who can reach the endpoint.

## Gotchas

- Do not expose configuration-bearing endpoints without reviewing how sensitive values are masked.
