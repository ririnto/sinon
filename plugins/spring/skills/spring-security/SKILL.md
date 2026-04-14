---
name: spring-security
description: >-
  This skill should be used when the user asks to "configure Spring Security", "secure Spring endpoints", "use method security", "understand the filter chain", "handle authentication or authorization in Spring", or needs guidance on Spring Security patterns.
---

# Spring Security

## Overview

Use this skill to design Spring Security configuration around a clear access model, explicit filter-chain behavior, and reviewable authorization rules. The common case is defining who can do what, then expressing that in one obvious `SecurityFilterChain`. Keep security policy readable before adding advanced chain splitting or protocol-specific details.

## Use This Skill When

- You are designing HTTP or method-level authorization in a Spring application.
- You need a default `SecurityFilterChain` shape you can paste into a project.
- You need to decide whether the app is browser-session, stateless API, JWT resource server, or a split surface.
- Do not use this skill when the main issue is general endpoint design without security policy, or security theory detached from Spring framework behavior.

## Common-Case Workflow

1. Start from the access model: who is allowed to do what.
2. Define endpoint or method-level rules clearly before touching low-level flags.
3. Keep one explicit `SecurityFilterChain` unless different request surfaces truly need different models.
4. Add CSRF, CORS, session behavior, and resource-server wiring deliberately based on the client model.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

## First Runnable Commands or Code Shape

Start with one explicit filter chain:

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET, "/public/**").permitAll()
                    .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .build();
}
```

---

*Applies when:* you need the default starting point for HTTP authorization in a Spring app.

## Ready-to-Adapt Templates

Basic authenticated app:

```java
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/public/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
```

---

*Applies when:* the app is a simple authenticated HTTP service.

Stateless API:

```java
@Bean
SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .build();
}
```

---

*Applies when:* the application is actually a stateless API and not a browser-session app.

Method-security extension:

```java
@Configuration
@EnableMethodSecurity
class SecurityConfig {
}

@PreAuthorize("hasRole('ADMIN')")
void deleteOrder(Long id) {
}
```

---

*Applies when:* URL shape alone is not enough to express the access rule.

JWT resource server:

```java
@Bean
SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    return http
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
}
```

---

*Applies when:* the API receives JWT bearer tokens rather than basic auth or login forms.

## Validate the Result

Validate the common case with these checks:

- the access model can be read directly from the filter chain without guessing
- broad `permitAll()` matchers do not sit above more specific protected rules
- CSRF is only disabled when the client model really justifies it
- multiple chains exist only when request surfaces genuinely need different security behavior

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| translating an access model into matcher structure | `./references/security-patterns.md` |
| concrete recipes for stateless API, JWT, multiple chains, or method security | `./references/security-config-recipes.md` |

## Invariants

- MUST start from the access model, not random filter tweaks.
- SHOULD keep security rules explicit and reviewable.
- MUST minimize accidental open access.
- SHOULD use method security only where endpoint-level rules are insufficient.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| starting from random filter flags instead of access rules | the security policy becomes hard to review | define the access model first, then map it into the chain |
| adding broad `permitAll()` matchers too early | sensitive paths can become accidentally open | order rules from most specific to most general |
| mixing several unrelated chains without a clear request split | the request surface becomes harder to reason about | keep one obvious chain unless different surfaces truly demand more |

## Scope Boundaries

- Activate this skill for:
  - Spring Security HTTP and method-level protection
  - filter-chain configuration
  - Spring-specific authn/authz design
- Do not use this skill as the primary source for:
  - general endpoint design without a security focus
  - generic non-Spring security theory detached from framework behavior
