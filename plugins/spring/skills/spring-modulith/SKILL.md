---
metadata:
  reference:
    Spring Modulith:
      version: 2.1.1
      url:
        - https://docs.spring.io/spring-modulith/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/modulith/spring-modulith-bom/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/modulith/spring-modulith-bom/2.1.1/spring-modulith-bom-2.1.1.pom
name: spring-modulith
description: >-
  Use Spring Modulith for module boundaries, named interfaces, events, publication, and interaction tests.
---

# Spring Modulith

## Boundaries

Use `spring-modulith` for package-level application module boundaries, named interfaces, module verification, module events, and module-focused tests.

- Use plain package refactoring or Java language guidance when the task is only about naming or moving classes without a module-boundary policy.
- Keep this skill focused on modular monolith structure inside one deployable application, not on splitting services across repositories.

## Module decisions

| Situation | Use |
| --- | --- |
| Other modules may use only a subset of types | named interface |
| Cross-module reaction should happen after work completes | application event |
| Boundary drift must fail fast in CI | `ApplicationModules.verify()` |
| One module interaction needs isolated integration coverage | `@ApplicationModuleTest` |
| Module arrangement needs runtime metadata or startup verification | `@Modulithic` with `spring-modulith-runtime` |
| Events must survive listener failures | event publication registry |
| Events must be reliably forwarded to external systems | event outbox |
| Domain reacts to calendar boundaries | Moments |

Keep named interfaces small and intention-revealing.
Prefer events over direct internal bean calls when the interaction does not need immediate synchronous coupling.

When one module may depend on only specific neighbors, make that dependency rule explicit rather than relying on package conventions alone.

## Dependency baseline

Import the BOM and use the core and test starters for the common path.

Spring Modulith BOM `2.1.1` is a documented GA baseline compiled and tested against Spring Boot 4.1.x.
For a new BOM, check `org.springframework.modulith:spring-modulith-bom` on Maven Central for the latest stable release compatible with the project's Boot line.
Keep an existing BOM or version-catalog pin unless the task authorizes changing it.

For dependency configuration, see [examples](references/configuration-examples.md).

## Module setup

For module declarations, boundary verification, events, and detection, read [module setup](references/module-setup.md).
Use `@Modulithic` to define module arrangement; runtime metadata, startup verification, and dependency-ordered initializers also need `spring-modulith-runtime` and `spring.modulith.runtime.verification-enabled=true`.
Set `systemName` for human-readable documentation names.
Use `@ApplicationModuleListener` at cross-module event boundaries; it combines `@Transactional(propagation = REQUIRES_NEW)` and `@TransactionalEventListener`.
Use `package-info.java` declarations when modules and named interfaces should be explicit.
Direct subpackages of the main application package are modules by default; choose explicit annotations or a custom detection strategy when needed.
Use boundary verification for structural changes and module tests for interactions needing a Spring runtime.

## Coding procedure

1. Name modules after business capabilities, not technical layers.
2. Keep non-exported types package-private wherever practical so module boundaries are reinforced by code structure.
3. Use named interfaces to expose only the entry points other modules are allowed to depend on.
4. Publish application events when one module should react after another module completes work.
5. Use `@ApplicationModuleTest` when the changed interaction needs module-scoped integration coverage.
6. Treat boundary violations as architecture regressions, not optional warnings.

## Module test decisions

| Situation | Use |
| --- | --- |
| One module should run in isolation | `@ApplicationModuleTest(mode = STANDALONE)` |
| Test should see direct dependency modules | `@ApplicationModuleTest(mode = DIRECT_DEPENDENCIES)` |
| Test should see the full dependency tree | `@ApplicationModuleTest(mode = ALL_DEPENDENCIES)` |
| Event publication must be asserted directly | `PublishedEvents` or `AssertablePublishedEvents` |
| Fluent assertions on published events | `AssertablePublishedEvents` |
| Efferent dependency should stay mocked in the module test | `@MockitoBean` |
| Event-driven interaction needs richer async verification | `Scenario` API |

## Implementation examples

- For boundary verification test, see [the example](references/implementation-examples.md#boundary-verification-test).
- For module event publication, see [the example](references/implementation-examples.md#module-event-publication).
- For event listener in another module, see [the example](references/implementation-examples.md#event-listener-in-another-module).
- For module test shape, see [the example](references/implementation-examples.md#module-test-shape).
- For assertable published events test shape, see [the example](references/implementation-examples.md#assertable-published-events-test-shape).
- For named interface shape, see [the example](references/implementation-examples.md#named-interface-shape).
- For documentation generation shape, see [the example](references/implementation-examples.md#documentation-generation-shape).

## Testing checklist

Verify the affected module boundary or cross-module event through the application.

- Verify module verification runs in CI and fails on illegal dependencies.
- Verify public entry points are the only paths other modules use.
- Verify cross-module collaboration works through the intended event or named interface.
- Cover changed module interactions with integration tests when lower-level checks cannot prove the contract.
- Verify boundary tests stay green after refactors that move packages or listeners.

## Production checklist

- Keep module names, exported interfaces, and event types stable after other modules depend on them.
- Avoid leaking internal packages as informal public APIs.
- Keep cross-module synchronous calls rare and intentional.
- Treat verification failures as release blockers for architecture-sensitive applications.
- Generate module documentation from verified boundaries.
- Use runtime inspection when diagnosing module structure or startup behavior.

## References

- Open [references/named-interfaces.md](references/named-interfaces.md) when module exposure rules need explicit `allowedDependencies` or several named interface packages.
- Open [references/scenario-tests.md](references/scenario-tests.md) when event-driven module tests need richer `Scenario` verification than the ordinary module test path.
- Open [references/event-publication-registry.md](references/event-publication-registry.md) when module events must be tracked, replayed, or completed reliably after failures.
- Open [references/moments.md](references/moments.md) when the application reacts to business-relevant time events such as day, week, or month boundaries.
- Open [references/event-externalization.md](references/event-externalization.md) when module events must be published to external brokers (Kafka, AMQP, JMS) or forwarded via the outbox pattern (Namastack, JobRunr).
- Open [references/documentation-generation.md](references/documentation-generation.md) when module arrangement diagrams and canvases must be generated for developer documentation.
- Open [references/runtime-support.md](references/runtime-support.md) when the application needs startup verification, module initializer ordering, module-specific Flyway migrations, actuator endpoints, observability, or change-aware test execution.
