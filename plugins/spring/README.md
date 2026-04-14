---
title: Spring
description: >-
  Overview of the Spring plugin, its included skills, and practical Spring application workflow coverage.
---

Spring is a shared, skill-first plugin for Spring Boot and Spring Framework application work in the Sinon universal marketplace.

## Purpose

- Provide reusable Spring workflows that remain portable across Claude Code and Codex-style plugin systems.
- Keep skills practical, example-driven, and focused on real application tasks rather than framework trivia.
- Separate Spring application concerns from Java language, JDK tooling, and Kotlin language concerns.

## Included Skills

- `spring-boot-fundamentals`: Spring Boot application shape, configuration, beans, profiles, and startup conventions.
- `spring-test`: `@SpringBootTest`, slice tests, and Spring-oriented test scope decisions.
- `spring-transactions-events`: `@Transactional` boundaries, rollback rules, transactional events, and outbox-style consistency.
- `spring-observability`: Actuator exposure, Micrometer observations, tracing, baggage, and operator-facing diagnostics.
- `spring-security`: filter chain basics, HTTP security, method security, and security configuration patterns.
- `spring-web-mvc`: Spring MVC controllers, validation, exception handling, and REST endpoint structure.
- `spring-webflux`: Reactive endpoints, `Mono`/`Flux`, functional vs annotated style, and WebFlux flow design.
- `spring-integration`: Integration flows, channels, adapters, routers, and message-driven composition.
- `spring-batch`: Jobs, steps, chunk processing, retry/skip, and scaling-oriented batch design.
- `spring-scheduling`: `@Scheduled`, `TaskScheduler`, `SchedulingConfigurer`, cron/fixed-rate/fixed-delay scheduling, dynamic task registration/cancellation, and lightweight in-process job design.
- `spring-quartz`: Quartz jobs, triggers, persistent job stores, runtime job/trigger control, operational scheduling management, clustering-oriented properties, and Spring Boot Quartz integration.
- `spring-data-jpa`: JPA entity mapping, repository methods, pagination, and transactional persistence patterns.
- `spring-data-jdbc`: Aggregate-oriented relational access with Spring Data JDBC.
- `spring-data-r2dbc`: Reactive relational access, repository shape, and non-blocking persistence patterns.
- `spring-data-redis`: Redis repositories, templates, key-space patterns, expiration, and messaging.
- `spring-cloud-gateway`: Route predicates, filters, backend integration, and gateway policy structure.
- `spring-cloud-kubernetes`: Discovery, ConfigMap and Secret usage, namespace and RBAC-aware Spring patterns.
- `spring-kafka`: `KafkaTemplate`, `@KafkaListener`, retry/error handling, and testing patterns.

## When to Use Which Skill

- Spring Boot application shape, configuration, bean wiring, and startup conventions belong in the fundamentals guidance.
- Spring test-slice choice, Spring Boot test scaffolding, and generic Spring context test strategy belong in the Spring testing guidance.
- Transaction boundaries, post-commit side effects, and outbox-style consistency belong in transaction and event guidance.
- Actuator exposure, metrics, tracing, and health probes belong in observability guidance.
- Filter chain, HTTP security, and method-security structure belong in security guidance.
- Servlet-style REST controllers, validation, and exception handling belong in MVC guidance.
- Reactive endpoints, `Mono`/`Flux`, and `WebClient` design belong in reactive web guidance.
- Adapter chains, channels, routers, and generic message-flow modeling belong in integration guidance.
- Kafka producer and consumer behavior, listener delivery semantics, embedded-Kafka listener verification, and retry/DLT behavior belong in Kafka-focused messaging guidance.
- Chunk processing, retry/skip policy, and large-scale job structure belong in batch guidance.
- App-local cron jobs, `@Scheduled`, and `TaskScheduler` usage belong in scheduling guidance.
- Durable job identity, restart survival, JDBC-backed state, and cluster-grade triggers belong in Quartz-oriented guidance.
- Persistence modeling belongs in the matching Spring Data guidance for JPA, JDBC, R2DBC, or Redis.
- Edge routing and filter policy belong in gateway guidance.
- Kubernetes runtime metadata, ConfigMap and Secret integration, and cluster-aware Spring behavior belong in Kubernetes guidance.

Typical workflow:

1. Establish the application shape first.
2. Add Spring-aware tests when behavior needs to be locked with framework context.
3. Bring in security guidance when authentication, authorization, or filter-chain concerns are in scope.
4. Use transaction and event guidance when consistency crosses service, event, or messaging boundaries.
5. Add observability guidance when the real problem is health, metrics, traces, or operator surface design.
6. Choose the concrete web, data, messaging, scheduling, or cloud guidance for the active subsystem.
7. Keep Java syntax, JVM diagnostics, and JDK packaging questions in the Java- or JDK-focused plugin surfaces.

Testing boundary:

- Tests that load Spring context behavior, Spring MVC/WebFlux infrastructure, Spring Data slices, or Spring-managed integrations belong in Spring testing guidance.
- Kafka listener tests whose contract depends on delivery semantics, retry, dead-letter handling, or embedded Kafka belong in Kafka-focused messaging guidance.
- Pure unit tests that do not need Spring context belong in language- or platform-level testing guidance outside this plugin.

## Scope Boundaries

Spring stays responsible for Spring-specific annotations, configuration, repository abstractions, messaging integration, cloud integration, and Spring testing patterns.

Keep these in other plugins:

- Java syntax, records, sealed types, and general language design.
- JDK tools, JVM diagnostics, and GC analysis.
- Kotlin language, coroutines, and Kotlin-native testing style.

Spring-specific coroutine controllers, `WebClient` usage, and reactive request handling belong in Spring guidance. General coroutine and Flow design outside Spring framework behavior belongs outside this plugin.

Scheduling boundary:

- App-local scheduled work with `@Scheduled`, `TaskScheduler`, and dynamic trigger registration belongs in lightweight scheduling guidance.
- Stable `JobKey` or `TriggerKey` identity, scheduler-level inventory or pause/resume control, restart survival, JDBC-backed scheduling state, and Quartz-specific triggers, calendars, or cluster-oriented properties belong in Quartz-oriented guidance.

## Design Principles

- Prefer working application slices over isolated annotation lists.
- Keep examples minimal but runnable in spirit.
- Route to the smallest Spring skill that matches the task.
- Keep `SKILL.md` self-contained and usable on its own; use `references/` only for supplemental decision aids and longer notes.
- Spring reference files in `references/` are expected to contain concrete additive examples (code, config, command snippets) and must not devolve into prose-only rule summaries; prose explains the example, the example proves the rule.

## Installation

Install from Sinon:

```bash
/plugin install spring@sinon
```

For local development:

```bash
cc --plugin-dir /path/to/sinon/plugins/spring
```
