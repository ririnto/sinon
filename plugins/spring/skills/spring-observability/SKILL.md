---
name: spring-observability
description: >-
  This skill should be used when the user asks to "expose Actuator safely", "add metrics or tracing to a Spring app", "use ObservationRegistry or @Observed", "configure health probes", or needs guidance on Micrometer and Spring Boot operator surfaces.
---

# Spring Observability

## Overview

Use this skill to design Spring Actuator exposure, Micrometer metrics, tracing, and operator-facing diagnostics around real production questions. The common case is one deliberate Actuator surface, one consistent metric and observation vocabulary, and one trace-correlation setup that helps operators understand the system without leaking sensitive or high-cardinality data.

## Use This Skill When

- You are adding Spring Boot Actuator endpoints or management-port policy.
- You need Micrometer metrics, `ObservationRegistry`, `@Observed`, or tracing guidance.
- You need health groups, readiness/liveness probes, or log-to-trace correlation.
- Do not use this skill when the task is only one subsystem-specific metric with no broader operator surface question.

## Common-Case Workflow

1. Start from the operator question: health, metrics, traces, scheduled work visibility, or runtime diagnostics.
2. Expose the smallest Actuator surface that solves the operational need.
3. Keep metrics low-cardinality and map observations to stable names.
4. Add trace and log correlation only where it helps real diagnosis and downstream propagation.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

## First Runnable Commands or Code Shape

Start with one deliberate Actuator exposure surface:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
```

---

*Applies when:* the application is moving beyond local development and needs a production-ready management surface.

## Ready-to-Adapt Templates

Custom observation — one explicit observation name with low-cardinality metadata:

```java
@Service
class InvoiceService {

    private final ObservationRegistry observationRegistry;

    InvoiceService(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    Invoice issue(InvoiceCommand command) {
        return Observation.createNotStarted("invoice.issue", observationRegistry)
                .lowCardinalityKeyValue("channel", command.channel())
                .observe(() -> doIssue(command));
    }

    private Invoice doIssue(InvoiceCommand command) {
        return new Invoice();
    }
}
```

HTTP client instrumentation — use Boot-managed builder so client observations attach normally:

```java
@Configuration
class ClientConfig {

    @Bean
    RestClient orderRestClient(RestClient.Builder builder) {
        return builder.baseUrl("https://orders.internal").build();
    }
}
```

Health groups for probes — explicit readiness and liveness groups:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: db,redis,diskSpace
        liveness:
          include: ping
```

## Validate the Result

Validate the common case with these checks:

- Actuator exposure includes only the endpoints operators really need
- management endpoint detail and security policy are explicit
- custom metrics use stable low-cardinality names and tags
- HTTP server and client instrumentation use Boot-managed builders or auto-configuration paths
- trace and log correlation help diagnosis without leaking sensitive baggage values casually

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| endpoint exposure, security, health groups, or management-port design | `./references/actuator-endpoints.md` |
| metric naming, observation APIs, timers, tags, or HTTP observations | `./references/metrics-and-observations.md` |
| trace propagation, baggage, MDC correlation, or OTLP export | `./references/tracing-and-correlation.md` |

## Invariants

- MUST treat Actuator as an operator surface, not a dump of every available endpoint.
- MUST keep metric names and tags stable and low-cardinality.
- SHOULD use Spring's observation APIs instead of one-off ad hoc timing code when shared visibility matters.
- SHOULD separate readiness and liveness when orchestration semantics matter.
- MUST protect sensitive management detail deliberately.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| exposing `*` management endpoints by default | operator surfaces leak more internals than necessary | include only the endpoints the environment actually needs |
| adding high-cardinality tags such as user ID to metrics | storage cost and cardinality explode quickly | keep metrics low-cardinality and move high-cardinality context to tracing only |
| constructing HTTP clients manually with `new` | Boot's instrumentation and shared policy can be bypassed | build clients from the managed builder |
| treating health, metrics, and tracing as independent afterthoughts | operators get fragmented diagnostics | design one coherent Actuator and observation surface |

## Scope Boundaries

- Activate this skill for:
  - Actuator endpoint strategy
  - Micrometer metrics and observations
  - tracing, baggage, and correlation
- Do not use this skill as the primary source for:
  - subsystem-specific business logic
  - generic monitoring vendor setup outside Spring Boot concepts
  - one isolated logger configuration change with no operator-surface impact
