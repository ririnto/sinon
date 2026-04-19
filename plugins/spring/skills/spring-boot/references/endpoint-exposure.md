# Spring Boot endpoint exposure

Open this reference when the blocker is Actuator endpoint exposure policy.

Endpoints such as `env`, `configprops`, `mappings`, `conditions`, or `loggers` can be operationally useful but should be exposed deliberately.

## Validation rule

Verify the exposed endpoint list matches the intended operational surface and nothing more.
