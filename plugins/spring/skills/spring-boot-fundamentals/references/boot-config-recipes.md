---
title: Spring Boot Configuration Recipes
description: >-
  Reference for conditional beans, override-friendly wiring, and operational config diagnostics.
---

Use this reference when the blocker is no longer "what is Spring Boot?" but "how should this be wired?"

## Configuration Properties Recipe

Prefer grouped configuration over many scattered `@Value` fields.

Use this shape when:

- several settings belong to one subsystem
- you want validation and discoverability
- the same settings are injected into multiple beans

## Startup Hook Recipe

Use startup hooks sparingly for validation, preloading, or controlled bootstrap work. Do not hide business workflows in startup hooks.

## Override-Friendly Wiring Nuance

When providing infrastructure beans that applications or libraries may also define, wrap them with `@ConditionalOnMissingBean` to let callers override without silencing Boot's default. This pattern is especially important in library code where the application may already supply an equivalent bean.

Use `@ConditionalOnMissingBean` only when the bean is a reasonable default; do not use it to silently skip configuration the application explicitly needs, because the absence will be hard to diagnose.

## Config Precedence Checklist

Spring Boot resolves configuration in a specific order. Check these before blaming a configuration problem on the application:

1. Packaged default properties (inside the jar)
2. Environment variables
3. Command-line arguments
4. Externalized property files for active profiles
5. Platform-provided configuration such as Kubernetes downward API or Docker secrets
6. Config imports and additional externalized property sources

When a setting appears to be ignored, work down this list to find which later entry is overriding it.

## Operational Diagnostics

### Debug auto-configuration condition evaluation

Use Boot's built-in diagnostics before assuming a configuration bug is mysterious:

```bash
java -jar app.jar --debug
```

The conditions endpoint (`management.endpoints.web.exposure.include: conditions`) shows which auto-configuration paths matched and which did not, and why. Keep it secured and operator-only when exposed over HTTP. Use it when:

- one auto-configuration path should have matched but did not
- a bean exists or disappears only under some profile or classpath combinations
- you need to verify that a conditional bean is being created as expected

### Profile-specific configuration

Profile-specific property files (`application-{profile}.yaml` or `application-{profile}.properties`) override the base file only for the values they contain. They do not require a matching `@Configuration` class; Spring Boot activates profile-specific property sources automatically when the profile is active.

Use profile-specific beans only when behavior, not just values, truly changes. If only endpoint URLs or connection strings differ, prefer property overrides first.

## Common Mistakes

- building one `@Configuration` class per small property instead of grouping related settings
- mixing infrastructure bean registration with business orchestration in the same class
- using profiles for every environment concern instead of using plain property overrides first
- forgetting that later property sources in the precedence chain override earlier ones
