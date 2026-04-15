---
name: spring-boot-fundamentals
description: >-
  Use this skill when the user asks to "set up a Spring Boot app", "explain Spring Boot structure", "configure beans or profiles", "use configuration properties", or needs guidance on Spring Boot application fundamentals.
---

# Spring Boot Fundamentals

## Overview

Use this skill to shape a Spring Boot application around clear startup, configuration, bean wiring, environment boundaries, and operator-ready bootstrap behavior. The common case is building one obvious Boot entry point, one grouped configuration-properties model, one explicit infrastructure boundary, and one predictable startup/runtime story before feature-specific code spreads across the app. Keep business logic out of bootstrap code and keep runtime settings grouped by subsystem.

## Use This Skill When

- You are bootstrapping a new Spring Boot application.
- You are deciding where `@SpringBootApplication`, `@Bean`, profiles, configuration properties, and bootstrap/runtime diagnostics belong.
- You need a default Spring Boot application shape you can paste into a project.
- Do not use this skill when the main problem is MVC endpoint design, reactive flows, persistence mapping, or Spring Security policy.

## Common-Case Workflow

1. Identify the application boundary and what the app is responsible for.
2. Keep one obvious `@SpringBootApplication` entry point unless the module structure truly requires more.
3. Use constructor injection for application services and explicit `@Bean` methods for infrastructure.
4. Group external settings with configuration properties and use profiles mostly for value changes, not code sprawl.
5. Keep startup diagnostics, shutdown behavior, and auto-configuration visibility explicit instead of accidental.

## Minimal Setup

Start from a Boot application with one concrete starter and one grouped configuration section:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

```yaml
app:
  base-url: https://example.org
  timeout: 5s
```

## First Runnable Commands or Code Shape

Start from one visible application entry point plus one grouped configuration-properties record:

```java
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

@ConfigurationProperties(prefix = "app")
public record AppProperties(String baseUrl, Duration timeout) {
}
```

---

*Applies when:* you need the default Spring Boot shape before adding subsystem-specific code.

## Ready-to-Adapt Templates

Single-entry Boot app:

```java
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

---

*Applies when:* every normal Boot application that does not need unusual bootstrap layering.

Configuration-properties wiring:

```java
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
class DemoApplication {
}

@ConfigurationProperties(prefix = "app")
record AppProperties(String baseUrl, Duration timeout) {
}
```

---

*Applies when:* several related settings belong to one subsystem.

Override-friendly infrastructure bean:

```java
@Configuration
class ClockConfig {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

---

*Applies when:* the application needs a shared infrastructure object such as `Clock`, client builders, or mappers.

Startup validation hook:

```java
@Component
class StartupVerifier implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        // verify required runtime assumptions
    }
}
```

---

*Applies when:* you need controlled bootstrap validation or preflight checks.

Auto-configuration visibility:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: conditions
```

---

*Applies when:* startup wiring or conditional configuration behavior is hard to explain from code inspection alone.

## Validate the Result

Validate the common case with these checks:

- the application entry point is easy to find immediately
- grouped settings use `@ConfigurationProperties` when several values belong together
- infrastructure beans live in configuration code, not business services
- profile differences are mostly values rather than duplicated class graphs
- startup behavior and auto-configuration diagnostics are visible through standard Boot mechanisms when needed

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| package layout, bean placement, or profile strategy | `./references/boot-structure.md` |
| conditional beans, startup hooks, or configuration-property recipes | `./references/boot-config-recipes.md` |

## Invariants

- MUST keep the application entry point easy to find.
- SHOULD prefer constructor injection.
- SHOULD group related settings with configuration properties.
- MUST keep framework bootstrap separate from business logic.
- SHOULD make startup diagnostics and shutdown policy explicit when operations depend on them.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| pushing business logic into `@Configuration` classes | bootstrap code becomes application orchestration | keep `@Configuration` for infrastructure creation only |
| scattering settings across many `@Value` fields | runtime configuration becomes harder to validate and reuse | group related settings under `@ConfigurationProperties` |
| using profiles for every environment concern | bean graphs become harder to reason about | prefer property changes first and profile-specific beans only when behavior truly differs |
| debugging auto-configuration only by guesswork | conditional wiring failures stay opaque | use `--debug` or the conditions endpoint intentionally |

## Scope Boundaries

- Activate this skill for:
  - Spring Boot app structure
  - bean and configuration boundaries
  - property binding and profile strategy
- Do not use this skill as the primary source for:
  - MVC or WebFlux endpoint design
  - persistence modeling or messaging integration details
  - Spring Security access-control design
