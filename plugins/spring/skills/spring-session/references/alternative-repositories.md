# Spring Session alternative repositories

Open this reference only when Redis and JDBC are both poor fits and an existing platform standard already mandates another repository.

Prefer Redis or JDBC unless the deployment already operates another repository as a stable platform dependency.

## Common alternatives

| Repository | Good fit |
| --- | --- |
| Hazelcast | The platform already runs Hazelcast and in-memory grid locality matters |
| MongoDB | The platform already standardizes on MongoDB and document-backed session storage is acceptable |
| Custom `SessionRepository` | Regulatory or proprietary infrastructure requirements make the built-in repositories unsuitable |

## Gotchas

- Do not pick an alternative repository just to avoid learning Redis or JDBC defaults.
- Do not skip store-backed tests for session creation, reuse, expiration, and principal lookup.
- Do not treat a custom repository as a small task; it changes durability and operational behavior at the core session layer.
