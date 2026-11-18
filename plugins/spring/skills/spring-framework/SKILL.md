---
metadata:
  reference:
    Spring Framework:
      version: 7.0.9
      url:
        - https://docs.spring.io/spring-framework/reference/index.html
        - https://docs.spring.io/spring-framework/reference/7.0.9/core/aop/proxying.html
        - https://docs.spring.io/spring-framework/reference/core/aop/using-aspectj.html
        - https://repo.maven.apache.org/maven2/org/springframework/spring-framework-bom/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/spring-framework-bom/7.0.9/spring-framework-bom-7.0.9.pom
name: spring-framework
description: >-
  Use core Spring for containers, lifecycle, transactions, events, validation, scheduling, JDBC, and TestContext.
---

# Spring Framework

## Boundaries

Use this skill for core Spring Framework APIs: container behavior, bean wiring, lifecycle hooks, transactions, events, scheduling, property binding, conversion, validation, resilience, plain JDBC, and TestContext-driven framework tests.

HTTP controllers and clients, security policy, and Boot auto-configuration are separate application boundaries rather than container-core work.

## Baseline requirements

Spring Framework 7 requires:

- JDK 17 minimum, with JDK 25 recommended as the latest LTS
- Jakarta EE 11 (Servlet 6.1, JPA 3.2, Bean Validation 3.1)
- JUnit 6 (JUnit 6 is the target test line)
- Kotlin 2.2 for Kotlin-based applications
- GraalVM 25 for native-image support

Only `jakarta.annotation` and `jakarta.inject` annotations are supported.
`javax.annotation` and `javax.inject` are no longer recognized.

## Spring Framework 7.0 API changes

Spring Framework 7.0 removed these APIs entirely:

- `spring-jcl` module (replaced by Apache Commons Logging 1.3.0)
- `javax.annotation` and `javax.inject` annotation support
- `ListenableFuture` (use `CompletableFuture`)
- `OkHttp3` support
- `Theme` support
- Undertow-specific classes (Undertow does not support Servlet 6.1)
- `org.webjars:webjars-locator-core` (use `webjars-locator-lite`)

Spring Framework 7.0 replacement targets:

- JUnit 4 TestContext support (`SpringRunner`, `SpringClassRule`, `AbstractJUnit4SpringContextTests`, etc.)
- Jackson 2.x support (use Jackson 3.x)
- Kotlin script templating (JSR 223 removal planned by Kotlin)

## Task scope

Keep module selection and bean wiring limited to the required capability.

## Modules and verification

Use only the Framework modules the application needs.
For a new BOM, check Maven Central for the latest stable release compatible with the project's Boot line; keep existing pins unless authorized to change them.
For wiring changes, use the narrowest existing TestContext-backed test and the target repository's native build command.
For module declarations or example test commands, read [modules and test commands](references/modules-and-test-commands.md).

## Container, beans, and boundaries

Prefer constructor injection and explicit bean graphs; use `BeanRegistrar` only when standard `@Bean` methods cannot express the required conditional registration.
Use the environment for external configuration, profiles for active bean sets, and explicit resource loading.
Use singleton beans by default and prototype only when lifecycle differences matter; HTTP request scopes belong to HTTP configuration.
Use lifecycle hooks only when needed and use one style consistently in a component graph.
Use application events for decoupled follow-up work, not ordinary method calls; keep events immutable and application-scoped.
Bind incoming values with `DataBinder`; add custom converters only when built-in conversion is insufficient.
Validate input at application boundaries and explicitly register method validation when needed.
In plain Spring enable transaction management with the appropriate transaction manager; put boundaries on service methods owning one business unit of work.
For configuration, proxies, bean scopes and lifecycle, events, SpEL expressions, binding, conversion, validation, or transaction examples, read [container, beans, events, and validation](references/container-beans-events-and-validation.md).

## Resilience, JDBC, and scheduling

Use retry for transient external failures and concurrency limits for hard resource ceilings; enable resilient methods when using the framework annotations.
Use plain JDBC templates for SQL without an ORM, named parameters where clearer, or `JdbcClient` for fluent statement settings.
`DriverManagerDataSource` and `SimpleDriverDataSource` are for testing or standalone use; use a real pool for a production data source managed by the application.
Enable async execution or scheduling explicitly; the default `SimpleAsyncTaskExecutor` starts a new thread per call, so configure an executor when pooling, queue depth, or naming matters.
Keep scheduled jobs idempotent and document cron expressions.
Treat combining `@Async` and `@Scheduled` as a proxy and executor decision and separate the methods when both behaviors are needed.
For retry, concurrency, JDBC, async, or scheduling implementation, read [resilience, JDBC, and scheduling](references/resilience-jdbc-and-scheduling.md).

## Cache, AOT, and testing

Enable caching only for a clear framework-managed cache boundary; treat AOT and native-image hints as an explicit task requirement.
Keep default implementation reflective and explicit unless AOT-friendly wiring is required.
Use the smallest TestContext integration that proves the behavior.
For caching, AOT hints, TestContext integration, context pausing, nested tests, or non-singleton bean overrides, read [cache, AOT, and TestContext](references/cache-aot-and-test-context.md).

## Testing checklist

Exercise the affected bean, advice, or transaction through its caller.
Do not require unrelated lifecycle, transaction, and validation tests for every change.

- Verify the application context loads with the intended bean graph.
- Verify transaction boundaries wrap the intended unit of work.
- Verify lifecycle or event callbacks run at the correct framework phase.
- Verify validation or conversion logic applies at the expected boundary.
- Verify framework tests stay narrow enough to isolate the intended integration behavior.

## Production checklist

- Keep bean wiring explicit enough that startup failures remain diagnosable.
- Avoid transaction boundaries broader than one business unit of work.
- Use lifecycle hooks sparingly and document why they are needed.
- Keep application events purposeful rather than turning them into hidden control flow.
- Treat Spring integration tests as part of the framework compatibility surface.

## References

- Open [aop cross cutting](references/aop-cross-cutting.md) when the task needs framework-level AOP beyond ordinary bean wiring.
- Open [async executor registration](references/async-executor-registration.md) when the task needs async exception handling, completion coordination with `Future` / `CompletableFuture`, or `TaskDecorator` for ThreadLocal propagation.
- Open [aspectj ltw](references/aspectj-ltw.md) when the task needs load-time weaving, `@Configurable`, or AspectJ join points beyond Spring AOP proxies.
- Open [container extension scopes](references/container-extension-scopes.md) when the blocker is container extension points, custom scopes, advanced listener infrastructure, or `@Configuration` lite-mode behavior.
- Open [environment and resources](references/environment-and-resources.md) when the task needs deeper control over profiles, property sources, or resource resolution beyond the common path.
- Open [plain jdbc wiring](references/plain-jdbc-wiring.md) when the task needs transaction-scoped connections, `SqlRowSet`, `RowMapper` reuse, or `DataSourceTransactionManager` with plain JDBC.
- Open [property binding conversion validation](references/property-binding-conversion-validation.md) when the task needs advanced data-binding rules, formatter/converter registration, or validation groups beyond the common path.
