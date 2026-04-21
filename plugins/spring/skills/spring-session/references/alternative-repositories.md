# Spring Session alternative repositories

Open this reference only when Redis and JDBC are both poor fits and an existing platform standard already mandates another repository.

Prefer Redis or JDBC unless the deployment already operates another repository as a stable platform dependency.

## Common alternatives

| Repository | Good fit |
| --- | --- |
| Community extension with a published 4.0.2-compatible release | The platform already mandates that store and the exact release line has been verified before adoption |
| Custom `SessionRepository` | Regulatory or proprietary infrastructure requirements make the built-in repositories unsuitable |

## Gotchas

- Do not pick an alternative repository just to avoid learning Redis or JDBC defaults.
- Do not assume older Hazelcast or MongoDB session modules are available on the current 4.0.2 line without checking their published artifacts first.
- Do not skip store-backed tests for session creation, reuse, expiration, and principal lookup.
- Do not treat a custom repository as a small task; it changes durability and operational behavior at the core session layer.
