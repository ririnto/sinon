# Spring Boot sanitization

Open this reference when the blocker is sanitizing sensitive Actuator values.

Keep access policy and sanitization explicit rather than relying on defaults you have not reviewed.

## Gotchas

- Do not expose configuration-bearing endpoints without reviewing how sensitive values are masked.
