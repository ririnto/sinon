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

- `spring-ai`: Spring AI patterns, model invocation, and vector-store integration.
- `spring-amqp`: AMQP bindings, template-based messaging, and RabbitMQ integration.
- `spring-authorization-server`: OAuth 2.1/OIDC authorization server setup, token endpoints, and client management.
- `spring-batch`: Jobs, steps, chunk processing, retry/skip, and scaling-oriented batch design.
- `spring-boot`: Spring Boot application shape, configuration, beans, profiles, and startup conventions.
- `spring-cloud`: Common Spring Cloud patterns, service discovery, and distributed configuration.
- `spring-cloud-data-flow`: Pipeline composition, task scheduling, and runtime operations.
- `spring-credhub`: CredHub credential management and Spring integration.
- `spring-data`: Shared Spring Data patterns, repository abstractions, and cross-store conventions.
- `spring-framework`: Core framework annotations, container behavior, and extension points.
- `spring-graphql`: GraphQL endpoint setup, schema execution, and GraphQL-specific testing.
- `spring-grpc`: gRPC service definition, channel customization, and in-process testing.
- `spring-hateoas`: Hypermedia-driven APIs, HAL forms, and entity links.
- `spring-integration`: Integration flows, channels, adapters, routers, and message-driven composition.
- `spring-kafka`: `KafkaTemplate`, `@KafkaListener`, retry/error handling, and testing patterns.
- `spring-ldap`: LDAP authentication, OpenLDAP integration, and ODM-based repository patterns.
- `spring-modulith`: Spring Modulith patterns, event publication registry, and module-boundary testing.
- `spring-pulsar`: Apache Pulsar producers, consumers, and Spring integration.
- `spring-rest-docs`: API documentation via Spring REST Docs with Asciidoctor.
- `spring-security`: Filter chain basics, HTTP security, method security, and security configuration.
- `spring-session`: HTTP session abstraction, Redis/JDBC store, and WebSocket session integration.
- `spring-shell`: Interactive CLI shells, command registration, and terminal UI styling.
- `spring-statemachine`: State machine setup, transitions, pseudo-states, and persistence.
- `spring-vault`: Vault secret handling, lease renewal, and secrets-as-code patterns.
- `spring-web-flow`: Flow-scoped web sessions, conversation management, and stateful navigation.
- `spring-web-services`: SOAP endpoints, WS-Security, and client-variant patterns.

## When to Use Which Skill

- Spring Boot application shape, configuration, bean wiring, and startup conventions belong in `spring-boot` guidance.
- Core framework annotations, container behavior, and extension points belong in `spring-framework` guidance.
- Spring test-slice choice, Spring Boot test scaffolding, and generic Spring context test strategy belong in `spring-boot` and `spring-framework` guidance.
- Filter chain, HTTP security, and method-security structure belong in `spring-security` guidance.
- Servlet-style REST controllers, validation, and exception handling belong in `spring-framework` guidance.
- Reactive endpoints, `Mono`/`Flux`, and `WebClient` design belong in `spring-framework` guidance.
- Hypermedia-driven APIs, HAL forms, and entity links belong in `spring-hateoas` guidance.
- Adapter chains, channels, routers, and generic message-flow modeling belong in `spring-integration` guidance.
- Kafka producer and consumer behavior, listener delivery semantics, embedded-Kafka listener verification, and retry/DLT behavior belong in `spring-kafka` guidance.
- AMQP bindings, template-based messaging, and RabbitMQ integration belong in `spring-amqp` guidance.
- Apache Pulsar producers, consumers, and Spring integration belong in `spring-pulsar` guidance.
- Chunk processing, retry/skip policy, and large-scale job structure belong in `spring-batch` guidance.
- Persistence modeling, JPA, JDBC, R2DBC, or Redis patterns belong in `spring-data` guidance.
- GraphQL endpoint setup, schema execution, and GraphQL-specific testing belong in `spring-graphql` guidance.
- gRPC service definition, channel customization, and in-process testing belong in `spring-grpc` guidance.
- SOAP endpoints, WS-Security, and client-variant patterns belong in `spring-web-services` guidance.
- Batch pipelines, task scheduling, and runtime operations belong in `spring-cloud-data-flow` guidance. Batch job structure, chunk processing, retry/skip, and scaling-oriented batch design belong in `spring-batch` guidance.
- Flow-scoped web sessions, conversation management, and stateful navigation belong in `spring-web-flow` guidance.

Typical workflow:

1. Establish the application shape first using `spring-boot` and `spring-framework`.
2. Add Spring-aware tests when behavior needs to be locked with framework context.
3. Bring in `spring-security` guidance when authentication, authorization, or filter-chain concerns are in scope.
4. Use `spring-integration`, `spring-kafka`, `spring-amqp`, or `spring-pulsar` when consistency crosses messaging boundaries.
5. Choose the concrete web, data, messaging, or cloud guidance for the active subsystem.
6. Java syntax, JVM diagnostics, and JDK packaging questions belong in Java- or JVM-focused guidance.

Testing boundary:

- Tests that load Spring context behavior, Spring MVC/WebFlux infrastructure, Spring Data slices, or Spring-managed integrations belong in Spring testing guidance covered by `spring-boot` and `spring-framework`.
- Kafka listener tests whose contract depends on delivery semantics, retry, dead-letter handling, or embedded Kafka belong in `spring-kafka` guidance.
- Pure unit tests that do not need Spring context belong in language- or platform-level testing guidance.

## Scope Boundaries

Spring stays responsible for Spring-specific annotations, configuration, repository abstractions, messaging integration, cloud integration, and Spring testing patterns.

These topics fall outside Spring's scope:

- Java syntax, records, sealed types, and general language design.
- JDK tools, JVM diagnostics, and GC analysis.
- Kotlin language, coroutines, and Kotlin-native testing style.

Spring-specific coroutine controllers, `WebClient` usage, and reactive request handling belong in Spring guidance. General coroutine and Flow design outside Spring framework behavior belongs in reactive or Kotlin-focused guidance.

Scheduling boundary:

- App-local scheduled work with `@Scheduled`, `TaskScheduler`, and dynamic trigger registration belongs in `spring-framework` guidance.
- `spring-batch` covers job identity, restart survival, and batch job state management.
- Pipeline composition, task scheduling, and runtime operations belong in `spring-cloud-data-flow` guidance.

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
