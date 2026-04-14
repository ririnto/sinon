---
title: Spring Boot Structure Reference
description: >-
  Reference for Spring Boot application structure, bean placement, and configuration layout.
---

Use this reference when the real question is how to shape a Boot application before feature-specific framework code spreads everywhere.

## Recommended Application Layout

- one visible `@SpringBootApplication` entry point
- package boundaries that separate web, application, domain, and infrastructure concerns
- explicit infrastructure beans for clocks, clients, encoders, and adapters
- configuration properties for grouped runtime settings

Suggested package shape:

```text
com.example.demo
├── DemoApplication
├── config
├── web
├── application
├── domain
└── infrastructure
```

## Bean Placement Rules

Put these in `@Configuration` classes:

- infrastructure beans
- client builders
- serializers and mappers when shared widely
- externally managed resources

Keep these out of `@Configuration` classes:

- business workflows
- request orchestration
- persistence logic

## `@Value` vs `@ConfigurationProperties`

Use `@Value` only for one-off simple values.
Use `@ConfigurationProperties` when:

- the settings belong together
- you need validation or reuse
- the property set may grow

## Component vs Bean Rule

- use `@Component` or stereotype annotations for app-owned behavior
- use `@Bean` for third-party types or infrastructure creation logic

## Profile Strategy

Prefer profile-specific values first.
Use profile-specific bean graphs only when runtime behavior actually changes.

## Web Application Type Rule

Keep the application's web stack explicit when mixed dependencies might confuse Boot's deduction.

- servlet stack → normal MVC Boot app
- reactive stack → WebFlux Boot app
- explicit override only when the default deduction would be misleading

## Auto-Configuration Visibility Rule

When startup behavior is surprising, make the condition evaluation visible instead of guessing.

- `--debug` enables the conditions report on startup
- the Actuator `conditions` endpoint surfaces the same idea through the management plane

## Review Checklist

- Can a newcomer find the app entry point immediately?
- Are properties grouped by subsystem?
- Are infrastructure beans isolated from business logic?
- Are profile differences mostly data, not code?
- Is startup or conditional wiring diagnosable without stepping through random bootstrap code?
