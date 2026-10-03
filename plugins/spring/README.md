---
description: >-
  Spring plugin skills, architecture routing, scope boundaries, and installation.
metadata:
  reference:
    OpenAI Codex plugins:
      url: https://developers.openai.com/plugins/build/plugins.md
---

# Spring

Use Spring skills for application configuration, web endpoints, persistence, security, messaging, and operations.
Use `spring-architect` for architecture and component design across those areas.

## Purpose

- Provide reusable Spring workflows that remain portable across Claude Code plugin installations.
- Provide application examples and task-specific technical references.
- Keep Java language, JDK tooling, and Kotlin language concerns in their owning guidance.

## Included Skills

- `spring-ai`: Spring AI patterns, model invocation, and vector-store integration.
- `spring-amqp`: AMQP bindings, template-based messaging, and RabbitMQ integration.
- `spring-authorization-server`: OAuth 2.1 and OIDC provider configuration, registered clients, token issuance, signing keys, and protocol endpoints.
- `spring-batch`: Jobs, steps, chunk processing, retry/skip, and scaling-oriented batch design.
- `spring-boot`: Spring Boot application shape, configuration, beans, profiles, and startup conventions.
- `spring-cloud`: Common Spring Cloud patterns, service discovery, distributed configuration, load-balanced clients, and circuit-breaker wiring.
- `spring-cloud-data-flow`: SCDF stream and task estate operations, app registration, schedules, platform accounts, and troubleshooting.
- `spring-data`: Shared Spring Data patterns, repository abstractions, and cross-store conventions (JPA, JDBC, MongoDB, Redis, R2DBC).
- `spring-framework`: Core container configuration, bean lifecycle, transactions, events, scheduling, resilience, JDBC, and TestContext.
- `spring-graphql`: GraphQL endpoint setup, schema execution, and GraphQL-specific testing.
- `spring-grpc`: gRPC service definition, channel customization, and in-process testing.
- `spring-hateoas`: Hypermedia-driven APIs, HAL forms, and entity links.
- `spring-integration`: Integration flows, channels, adapters, routers, and message-driven composition.
- `spring-kafka`: `KafkaTemplate`, `@KafkaListener`, retry/error handling, and testing patterns.
- `spring-ldap`: directory queries, DN handling, ODM, repository patterns, and embedded directory tests.
- `spring-modulith`: Spring Modulith patterns, event publication registry, and module-boundary testing.
- `spring-pulsar`: Apache Pulsar producers, consumers, and Spring integration.
- `spring-rest-docs`: API documentation via Spring REST Docs with Asciidoctor.
- `spring-security`: Filter chains, HTTP security, method security, sessions, and bearer-token enforcement.
- `spring-session`: HTTP session abstraction, Redis/JDBC store, and WebSocket session integration.
- `spring-shell`: Interactive CLI shells, command registration, and terminal UI styling.
- `spring-statemachine`: State machine setup, transitions, pseudo-states, and persistence.
- `spring-vault`: Vault secret handling, KV property-source loading, transit encryption, and CredHub credential management.
- `spring-web`: Servlet MVC, reactive WebFlux, RestClient, WebClient, API versioning, and focused web tests.
- `spring-web-flow`: Stateful browser conversations, flow scopes, validation, exception handling, and flow execution tests.
- `spring-web-services`: SOAP endpoints, WS-Security, and client-variant patterns.

## Included Agents

- `spring-architect`: Spring architecture decisions, component boundaries, and cross-skill design routing.

`spring-architect` is a read-only leaf domain router for cross-skill Spring decisions.
It may load Spring skills but does not delegate to other agents.

## When to Use Which Skill

Select the skill for the active subsystem from the inventory above.
Use `spring-boot` or `spring-framework` configuration and test guidance when Boot wiring or container behavior affects the result.
Load additional Spring skills only for integration boundaries the task crosses.

### Testing Boundaries

- Tests that load generic Spring context behavior belong in `spring-framework`.
  MVC/WebFlux HTTP tests belong in `spring-web`.
  Boot slices and Boot-managed integration tests belong in `spring-boot`.
- Kafka listener tests whose contract depends on delivery semantics, retry, dead-letter handling, or embedded Kafka belong in `spring-kafka` guidance.
- Pure unit tests that do not need Spring context belong in language- or platform-level testing guidance.

## Scope Boundaries

Spring Cloud Data Flow coverage is for maintaining, operating, and migrating existing SCDF stream/task estates.
Treat new greenfield orchestration decisions as platform architecture work and verify the current SCDF maintenance status before recommending new adoption.

Spring stays responsible for Spring-specific annotations, configuration, repository abstractions, messaging integration, cloud integration, and Spring testing patterns.

These topics fall outside Spring's scope:

- Java syntax, records, sealed types, and general language design.
- JDK tools, JVM diagnostics, and GC analysis.
- Kotlin language, coroutines, and Kotlin-native testing style.

Spring-specific coroutine controllers, `WebClient` usage, and reactive request handling belong in Spring guidance.
General coroutine and Flow design outside Spring framework behavior belongs in reactive or Kotlin-focused guidance.

### Scheduling Boundaries

- Application local scheduled work with `@Scheduled`, `TaskScheduler`, and dynamic trigger registration belongs in `spring-framework` guidance.
- `spring-batch` covers job identity, restart survival, and batch job state management.
- Pipeline composition, task scheduling, and runtime operations belong in `spring-cloud-data-flow` guidance.

## Runtime Model

This package uses `.claude-plugin/plugin.json` in Claude Code and `.codex-plugin/plugin.json` in Codex.
Codex loads skills from the default `skills/` directory.
Claude Code also loads the plugin-root `spring-architect` agent.
The listed plugin-root agent remains a Claude Code surface.

## Plugin Layout

```text
plugins/spring/
+-- .claude-plugin/plugin.json
+-- .codex-plugin/plugin.json
+-- README.md
+-- agents/
|   +-- spring-architect.md
+-- skills/
    +-- spring-ai/
    +-- spring-amqp/
    +-- spring-authorization-server/
    +-- spring-batch/
    +-- spring-boot/
    +-- spring-cloud/
    +-- spring-cloud-data-flow/
    +-- spring-data/
    +-- spring-framework/
    +-- spring-graphql/
    +-- spring-grpc/
    +-- spring-hateoas/
    +-- spring-integration/
    +-- spring-kafka/
    +-- spring-ldap/
    +-- spring-modulith/
    +-- spring-pulsar/
    +-- spring-rest-docs/
    +-- spring-security/
    +-- spring-session/
    +-- spring-shell/
    +-- spring-statemachine/
    +-- spring-vault/
    +-- spring-web/
    +-- spring-web-flow/
    +-- spring-web-services/
```

## Installation

Install from Sinon:

```sh
claude plugin install spring@sinon
```

For local development:

```sh
claude --plugin-dir /path/to/sinon/plugins/spring
```
