---
metadata:
  reference:
    Spring Integration:
      version: 7.1.1
      url:
        - https://docs.spring.io/spring-integration/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/integration/spring-integration-bom/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/integration/spring-integration-bom/7.1.1/spring-integration-bom-7.1.1.pom
name: spring-integration
description: >-
  Use Spring Integration for message flows, channels, routers, gateways, pollers, and adapters.
---

# Spring Integration

## Boundaries

Use `spring-integration` for Enterprise Integration Patterns inside or at the edge of a Spring application.

- Direct broker APIs without an Integration flow are outside this skill's scope.
- Distributed-system infrastructure such as Config, Gateway, or general service-to-service wiring is outside this skill's scope.
- Keep domain logic out of the flow graph.
  - Flows should orchestrate message movement, routing, and adaptation.

## Core flow decisions

| Situation | Use |
| --- | --- |
| One caller hands off to one handler inline | `DirectChannel` |
| The flow needs buffering between producer and consumer | `QueueChannel` |
| One message fans out to several subscribers | publish-subscribe channel |
| Application code sends into the flow | messaging gateway |
| External system sends one-way events | inbound adapter |
| External system expects request-reply behavior | inbound gateway |

Choose the simplest channel and endpoint pair that matches the delivery semantics.
Add pollers only for sources that do not naturally push messages.

## Dependency baseline

Before recommending a new version, check each selected Maven artifact on Maven Central for its latest stable release compatible with the project's Boot and Framework lines.
Keep project BOM or version-catalog management and existing pins unless the task authorizes changing them.

Use the Boot starter for core Integration features and add only the protocol modules the flow actually needs.

The examples target Spring Integration 7.1.x.
Spring Boot 4.1.x manages that line.
Boot 3.5.x applications manage the Spring Integration 6.5.x line and therefore remain a separate compatibility branch.

For the core dependency declaration, see [the example](references/configuration-examples.md#core-baseline).

### Optional adapter and test modules

For optional adapter and test modules, see [example 1](references/configuration-examples.md#example-1-optional-adapter-and-test-modules).

### New modules in 7.1

For new modules in 7.1, see [example 2](references/configuration-examples.md#example-2-new-modules-in-71).

Remove any adapter or test module the flow does not actually use.

When Spring Boot already manages the module line, keep Spring Integration artifacts versionless.
Add an exact version only on a standalone path that intentionally pins Spring Integration outside Boot-managed dependency control.

## Spring Integration 7.1 flow design

When using HTTP outbound or inbound gateways, Redis, JMS, DSL-only mode, transformers, message stores, CloudEvents, or gRPC, read [7.1 flow design](references/version-7-1-flow-design.md).
Prefer `RestClient` for new HTTP outbound flows.
Transformers cannot produce null replies, and `requiresReply` cannot be changed.
HTTP and WebFlux inbound CORS defaults align with Spring MVC.
For HTTP or Redis configuration examples, read [configuration examples](references/configuration-examples.md).

## Flow setup

For gateway, basic flow, or error-channel setup, read [flow setup](references/flow-setup.md) and [configuration examples](references/configuration-examples.md).
Use `errorChannel` by default.

## Coding procedure

1. Choose channel semantics deliberately: direct, queued, publish-subscribe, or reactive.
2. Use filters, routers, splitters, and aggregators only when the flow actually needs them.
3. Keep message headers intentional and stable when downstream routing depends on them.
4. Add persistent stores only when idempotency, aggregation, resequencing, or poller state must survive restarts.
5. Use Java DSL for new code unless the surrounding application already standardized on another style.
6. Add runtime flow registration, reactive channels, or adapter-specific modules only when the ordinary direct flow is not enough.
7. For flow changes, test the intended message path and affected error or discard behavior.

## Basic DSL verbs

For new flows, default to DSL-first `handle(...)`.

| Need | DSL shape |
| --- | --- |
| Change the payload | `.transform(...)` |
| Drop or reroute some messages | `.filter(...)` |
| Choose the next channel from content or headers | `.route(...)` |
| Invoke application code at the endpoint boundary | `.handle(...)` |
| Fan one message out into parts | `.split(...)` |
| Rejoin related messages | `.aggregate(...)` |

## Endpoint composition rules

- Use a transformer when the payload shape changes.
- Use a filter when the flow should discard or reroute some messages.
- Use a router when the next channel depends on message content or headers.
- Use a service activator when application code should consume or reply to the current message at the endpoint boundary.
- Use a splitter and aggregator pair only when one message really must fan out and rejoin.

When a flow fans out and rejoins, define correlation, release, and timeout rules deliberately instead of assuming the default group behavior is correct for the business boundary.

## Implementation examples

For gateways, service activators, splitters, aggregators, pollers, or routers, read [implementation examples](references/implementation-examples.md).

For flow sketches, gateway channels, or poller shapes, read [output shapes](references/output-shapes.md).

## Testing checklist

Follow a representative message through the changed endpoint and error path.
Use the existing native test setup.

- Verify the intended message path through the graph.
- Verify discard, error-channel, or retry behavior on one representative failure path.
- Verify channel semantics match ordering and throughput expectations.
- Verify headers used for routing or correlation are actually present.
- Verify persistent stores are present only when restart or clustering semantics require them.
- Verify poller, retry, and endpoint advice configuration match the intended failure boundary.

## Production checklist

- Keep channel names, endpoint ids, and header conventions stable.
- Bound queues or use backpressure-aware channels where accumulation can grow.
- Make external adapters idempotent before adding retries.
- Put explicit timeouts on remote adapters and pollers.
- Keep error channels, retry advice, and circuit-breaker behavior observable.
- Treat flow tests and adapter contracts as part of the operational compatibility surface.

## References

Read the relevant reference when the named condition applies:

- [CloudEvents and gRPC](references/cloudevents-and-grpc.md): CloudEvents transformation or gRPC gateways.
- [Errors and retry](references/error-handling-and-retry-patterns.md): endpoint advice, retry, circuit breaking, or explicit error-channel routing.
- [Testing](references/testing-integration-flows.md): `@SpringIntegrationTest`, `MockIntegrationContext`, `noAutoStartup`, or graph-level assertions.
- [Pollers, transactions, stores](references/pollers-transactions-and-stores.md): pollers, transactional sources, aggregators, or persistent message stores.
- [Runtime and reactive flows](references/dsl-runtime-flows-and-reactive.md): runtime registration, subflows, reactive channels, or composed Java DSL flows.
- [Control bus](references/control-bus-and-system-management.md): inspect, start, stop, or observe endpoints in production.
- [Adapter selection](references/adapter-family-selection.md): protocol adapters or module boundaries for an external system.
- [Native AOT](references/native-aot-support.md): native-image adapter or reflection constraints.
