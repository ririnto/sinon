---
name: spring-test
description: >-
  Use this skill when the user asks to "test a Spring Boot app", "choose @SpringBootTest or a slice test", "use MockMvc or WebTestClient", "test Spring Data integration", or needs guidance on Spring testing patterns.
---

# Spring Test

## Overview

Use this skill to choose the smallest Spring-aware test scope that proves behavior without overbuilding the test context. The common case is picking one focused Spring test slice, not defaulting to full application integration. Start from the behavior that must be proven, then choose the narrowest Spring test surface that still captures the contract and the right `webEnvironment` when full Boot wiring is necessary.

## Use This Skill When

- You need to choose between `@WebMvcTest`, `@WebFluxTest`, `@DataJpaTest`, `@JdbcTest`, `@JsonTest`, `@RestClientTest`, and `@SpringBootTest`.
- You need to decide whether MockMvc or WebTestClient is the better fit.
- You need a default Spring test scaffold you can paste into a codebase.
- You need to choose the Spring test harness around a Kafka component, but not the Kafka-specific listener semantics, retry policy, or dead-letter behavior.
- Do not use this skill for pure unit tests that do not need Spring context at all.

## Common-Case Workflow

1. Start from the behavior that must be proven.
2. Choose the smallest Spring test slice that covers that behavior.
3. Mock only the boundary behind that slice.
4. Escalate to full `@SpringBootTest` only when multiple Spring subsystems truly need to work together or the real embedded server matters.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-test</artifactId>
  <scope>test</scope>
</dependency>
```

Use `spring-boot-starter-test` as the common dependency baseline instead of hand-assembling every Spring testing dependency.

## First Runnable Commands or Code Shape

Start with the narrowest MVC slice for controller contract testing:

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    OrderService orderService;
}
```

---

*Applies when:* the behavior is servlet MVC, validation, status codes, or JSON shape.

## Ready-to-Adapt Templates

MVC slice:

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    OrderService orderService;
}
```

---

*Applies when:* the contract is MVC-only and does not need full application wiring.

JPA slice:

```java
@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    OrderRepository orderRepository;
}
```

---

*Applies when:* the behavior is query or mapping correctness in JPA.

JDBC slice:

```java
@JdbcTest
class OrderRowMapperTest {

    @Autowired
    JdbcTemplate jdbcTemplate;
}
```

---

*Applies when:* the code under test is SQL or row-mapping logic rather than JPA entity semantics.

JSON slice:

```java
@JsonTest
class OrderResponseJsonTest {

    @Autowired
    JacksonTester<OrderResponse> json;
}
```

---

*Applies when:* the contract question is pure JSON shape, naming, or date formatting.

REST client slice:

```java
@RestClientTest(OrderClient.class)
class OrderClientTest {

    @Autowired
    MockRestServiceServer server;
}
```

---

*Applies when:* the code under test is a `RestClient`, `RestTemplate`, or similar Spring-managed HTTP client boundary.

WebFlux slice:

```java
@WebFluxTest(EventController.class)
class EventControllerTest {

    @Autowired
    WebTestClient webTestClient;
}
```

---

*Applies when:* the behavior is reactive endpoint contract testing.

Full integration:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationIT {
}
```

---

*Applies when:* a narrower slice would miss the interaction you must prove or the embedded server must really start.

Context reset after mutating the Spring context:

```java
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ContextMutatingIT {
}
```

---

*Applies when:* the test mutates singleton bean state, dynamic bean registration, or other application-context state that must not leak into later tests.

## Validate the Result

Validate the common case with these checks:

- the annotation scope is the smallest one that still proves the behavior
- only the dependency immediately behind the slice is mocked
- MVC tests use MockMvc and WebFlux tests use WebTestClient unless a full Boot test explicitly uses a client against a running server
- full `@SpringBootTest` appears only when several Spring subsystems genuinely matter
- `webEnvironment` is explicit when a full Boot test depends on a running server versus a mock environment
- `@DirtiesContext` appears only when a test really mutates Spring context state and ordinary rollback or fixture reset is not enough

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| slice-vs-full-context comparison details | `./references/testing-patterns.md` |
| deciding whether a broad Spring integration test is still too large | `./references/testing-patterns.md` |

## Invariants

- MUST choose the smallest Spring-aware test scope that proves the behavior.
- SHOULD prefer slice tests before full `@SpringBootTest`.
- MUST keep Spring integration tests purposeful, not default.
- SHOULD use MockMvc or WebTestClient only where they match the subsystem under test.
- SHOULD make `webEnvironment` explicit when full Boot integration tests are used.
- MUST treat `@DirtiesContext` as a narrow context-reset tool, not a default isolation mechanism.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| defaulting every test to `@SpringBootTest` | test cost grows while scope stays fuzzy | choose the smallest slice first |
| mixing controller, repository, and messaging assertions in one class | the contract under test becomes unclear | keep one Spring slice per test class where possible |
| mocking too deep into the slice | the test stops proving the Spring boundary | mock only the collaborator immediately behind the slice |
| using `@DirtiesContext` as a routine cleanup tool | context caching is defeated and test cost rises quickly | use normal rollback, fixture reset, or narrower slices first; reserve `@DirtiesContext` for actual context mutation |

## Scope Boundaries

- Activate this skill for:
  - Spring test-scope decisions
  - Spring Boot test annotations and utilities
  - Spring-aware integration testing strategy
  - Spring test-harness selection around Kafka components when the real question is slice choice or full-context scope
- Do not use this skill as the primary source for:
  - pure Java or Kotlin test style without Spring context
  - Kafka producer/consumer design, listener delivery semantics, retry, dead-letter handling, or Kafka-specific embedded-listener verification
