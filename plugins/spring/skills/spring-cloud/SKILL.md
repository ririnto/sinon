---
metadata:
  reference:
    Spring Cloud Release:
      url: https://docs.spring.io/spring-cloud-release/reference/index.html
    Spring Cloud Supported Versions:
      url: https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions
    Spring Cloud BOM:
      - version: 2025.1.3
        url:
          - https://repo.maven.apache.org/maven2/org/springframework/cloud/spring-cloud-dependencies/maven-metadata.xml
          - https://repo.maven.apache.org/maven2/org/springframework/cloud/spring-cloud-dependencies/2025.1.3/spring-cloud-dependencies-2025.1.3.pom
      - version: 2025.0.3
        url:
          - https://repo.maven.apache.org/maven2/org/springframework/cloud/spring-cloud-dependencies/maven-metadata.xml
          - https://repo.maven.apache.org/maven2/org/springframework/cloud/spring-cloud-dependencies/2025.0.3/spring-cloud-dependencies-2025.0.3.pom
    Spring Project Security Advisory Index:
      url: https://spring.io/security
name: spring-cloud
description: >-
  Use Spring Cloud for release-train alignment, configuration, discovery, load balancing, and resilience.
---

# Spring Cloud

Spring Cloud 2025.1.3 (Oakwood) is a documented stable release train.
Spring Cloud 2025.0.3 (Northfields) is a parallel Boot 3.5.x line, not a newer replacement.
Use the documented 2025.1.x examples only when the project's exact Boot release supports that train.

| Release Train | Boot Generation | Notes |
| --- | --- | --- |
| 2025.1.x (Oakwood) | 4.0.x | Documented GA line; Jackson 3, JSpecify, Spring Framework 7 |
| 2025.0.x (Northfields) | 3.5.x | Parallel GA line; Gateway artifact rename |
| 2024.0.x (Moorgate) | 3.4.x | Outside the documented OSS support policy |
| 2023.0.x (Leyton) | 3.3.x, 3.2.x | Outside the documented OSS support policy |

For new work, choose a release train within the documented OSS support policy unless the project has applicable commercial support.
Keep historical release trains only for compatibility and migration guidance.

## Boundaries

Use `spring-cloud` for release-train-aligned distributed application wiring, external configuration through ConfigData, refresh-aware configuration, service discovery, load-balanced downstream calls, and resilience patterns.

- Use narrower Spring Cloud branches when the task is specifically about Gateway, OpenFeign, Stream binders, Contract, Bus, Kubernetes, or Vault behavior.
- In-process integration flows inside one application are outside this skill's scope.

## Surface map

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| ConfigData client | the service needs externalized configuration through ConfigData | Vault-backed config applies in [references/vault-config.md](references/vault-config.md) or Kubernetes-backed config applies in [references/kubernetes-config.md](references/kubernetes-config.md) |
| Refresh-aware configuration | one bean must rebind after refresh | distributed refresh propagation applies in [references/bus-refresh.md](references/bus-refresh.md) |
| Service discovery and load-balanced clients | one service calls another by logical service id | Kubernetes-backed discovery applies in [references/kubernetes-discovery.md](references/kubernetes-discovery.md) or declarative clients are clearer in [references/openfeign-clients.md](references/openfeign-clients.md) |
| Circuit-breaker boundary | one remote call needs resilience | gateway routing is the boundary in [references/gateway-routing.md](references/gateway-routing.md) |
| Contract verification | provider-consumer compatibility is the real job | open [references/contract-testing.md](references/contract-testing.md) |
| Stream binders | the service actually publishes to or consumes from a broker | open [references/stream-binders.md](references/stream-binders.md) |
| Function catalog | the task is specifically about function beans or composition | open [references/function-catalog.md](references/function-catalog.md) |

## Task routing

Use the surface map for the affected configuration, discovery, client, or resilience boundary.
Before configuring ConfigData, refresh, discovery, load balancing, resilience, compatibility verification, or Data Flow applications, read [configuration examples](references/cloud-configuration.md).
For existing SCDF estate operations, use [spring-cloud-data-flow](../spring-cloud-data-flow/SKILL.md).
The SCDF command examples here illustrate application handoff, not authorization to change a running platform.

## Dependency baseline

Before choosing or changing dependencies, read [Spring Cloud dependencies](references/cloud-dependencies.md).
Import the Cloud BOM once and keep its managed modules versionless.
Verify the stable train against the exact Boot release; keep existing pins unless changes are authorized.
Keep discovery, Gateway, OpenFeign, bus, and binder dependencies out of the common path unless required.

## Coding procedure

### Distributed-system wiring

1. Keep release-train and Boot alignment explicit in the build.
2. Choose the smallest Spring Cloud module set that solves the current distributed-system need.
3. Keep service names, route ids, and external config import locations stable.
4. Use logical service ids consistently across discovery-backed and load-balanced calls.
5. Add retries and circuit breakers only around genuine remote boundaries.
6. Keep gateway policy and downstream client policy separate so each remains understandable.
7. For remote-call changes, test successful calls and affected downstream-failure behavior.

### Data Flow orchestration

1. Pick stream versus task first because deployment, lifecycle, and observability differ.
2. Keep application registration coordinates explicit and versioned.
3. Put platform-specific settings in deployment properties rather than baking them into app code.
4. Keep stream DSL definitions small and composable before introducing large topologies.
5. Treat task parameters and schedule definitions as operational contracts.
6. Verify deployment or launch status after every topology change.

Before implementing or interpreting distributed client, config, discovery, load-balancing, or Interface Client examples, or implementing, interpreting, deploying, operating, or verifying Data Flow, read [implementation and output examples](references/cloud-examples-and-outputs.md).
Prefer Interface Clients for new Boot 4.x work when suitable; retain OpenFeign for existing estates or Feign-specific requirements and do not migrate merely for syntax.

## Testing checklist

Exercise the affected client under its normal and degraded service conditions.
Runtime verification applies after authorized operations, not as a reason to deploy during an application-code task.

### Distributed-system wiring

- Verify the service boots with the intended release-train-managed dependencies.
- Verify external configuration resolves from the intended source.
- Verify one discovery-backed downstream call targets the expected logical service id.
- Verify one representative load-balanced remote call succeeds against the intended logical service.
- Verify one representative downstream failure path exercises the chosen retry or circuit-breaker rule.

## Production checklist

### Security advisories

Review Spring security advisories for the component versions used by the application.
Choose a compatible stable release train with component versions that include every relevant fix.
Verify the release train and managed component versions before deployment.

### Distributed-system wiring

- Keep Boot and Spring Cloud release-train compatibility explicit.
- Keep service ids, route ids, and config import locations stable.
- Add resilience features only around actual remote boundaries.
- Keep discovery-backed and load-balanced client behavior observable.
- Treat distributed-system wiring tests as part of the operational compatibility surface.
