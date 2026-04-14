---
title: Spring Testing Patterns Reference
description: >-
  Deeper test-scope decision rules, transactional semantics, slice customization, and fixture guidance.
---

Use this reference for depth that the main skill does not cover.

## Transaction Rule

- `@DataJpaTest` and `@JdbcTest` roll back each test transaction automatically
- `@WebMvcTest` and `@JsonTest` do not imply database rollback semantics because they are not database slices
- `@SpringBootTest` plus `@Transactional` gives test-managed rollback semantics only for in-process test interactions; do not assume it rolls back work performed through a live server started with `RANDOM_PORT` or `DEFINED_PORT`

## Context Cleanup Rule

Use `@DirtiesContext` only when a test mutates `ApplicationContext` state that should not survive into later tests.

- changing singleton bean state can justify `@DirtiesContext`
- dynamic bean registration or configuration mutation can justify `@DirtiesContext`
- ordinary transaction rollback, recreated fixtures, or per-test data setup should be preferred first
- `@DirtiesContext` removes the context from the cache, so repeated use makes the suite much slower

```java
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ContextMutatingIT {

    @Autowired
    FeatureFlagRegistry registry;

    @Test
    void flipsFeatureFlagForOneScenario() {
        registry.enable("beta-checkout");
    }
}
```

## Slice Customization Rule

Use `@Import` or a nested `@TestConfiguration` when a focused slice needs one extra bean, instead of escalating the whole test to full Boot wiring.

### Adding a bean to a slice with `@Import`

```java
@WebMvcTest(OrderController.class)
@Import(TestSecurityConfig.class)  // adds UserDetailsService or SecurityFilterChain for the slice
class OrderControllerSliceTest { }
```

### Adding a bean with nested `@TestConfiguration`

```java
@JsonTest
class OrderJsonTest {

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    Clock clock;  // injected only for this slice test class

    @Test
    void serializesWithFixedClock() { /* ... */ }
}
```

## Escalation Decision Aid

| Situation | Move to `@SpringBootTest`? |
| --- | --- |
| slice already covers the concern but one bean is missing | add `@Import` or nested `@TestConfiguration` instead |
| test needs `WebMvcTest` slice but also needs `@MockBean` for a deep collaborator | stay with `@WebMvcTest` + `@MockBean` |
| test needs real transaction semantics across multiple in-process data slices | escalate to `@SpringBootTest` + `@Transactional`, but not as a rollback guarantee for live-server HTTP tests |
| test needs full environment, `Environment`, or profile behavior | escalate to `@SpringBootTest` |
| test needs a running server to assert actual HTTP behavior | use `@SpringBootTest(webEnvironment = RANDOM_PORT)` only then |
| test mutates application context state and later tests must not see it | keep the same scope and add `@DirtiesContext` only if rollback or fixture reset is not enough |

## Fixture Rule

Mock only the boundary behind the slice you are testing.
Do not hide several different behaviors inside one large Spring test class.

## Common Mistakes

- Using full integration tests for behavior that a Spring slice already proves
- Mixing controller, repository, and messaging concerns into one test class
- Forgetting that a full running server changes both cost and failure surface compared with `webEnvironment = MOCK`

Canonical test-slice templates belong in the parent skill entrypoint, not as a local reference link.
