---
name: java-test
description: >-
  This skill should be used when the user asks to "write a JUnit test", "follow TDD in Java", "fix a failing Maven test", "configure Gradle tests", "set up Surefire or Failsafe", or needs guidance on Java test-first workflows.
---

# Java Test

## Overview

Use this skill to drive Java work through tests first, then implementation, then cleanup. The common case is writing the smallest failing JUnit 5 test, making the smallest production change, and keeping build-tool wiring separate from the test’s behavioral intent. Prefer direct, readable test scaffolds over testing-framework demos.

Example conventions:

- each file-oriented example keeps exactly one top-level root type
- the root declaration is a type, not a top-level method or field
- avoid unnecessary blank lines inside function bodies
- use `@Nested` when one behavior splits cleanly by scenario context
- use `@Timeout` for declarative per-test time limits and `assertTimeoutPreemptively` only when preemptive interruption is actually required

## Use This Skill When

- You are adding or fixing Java tests.
- You want to drive a bug fix or feature through red-green-refactor.
- You need a default JUnit 5, Mockito, or Awaitility testing shape.
- Do not use this skill when the main issue is dependency coordinate lookup or repository-wide build governance.

## Common-Case Workflow

1. Read the target production code and the nearest related tests.
2. Identify the requested behavior or bug before touching implementation.
3. Write the smallest failing JUnit 5 test that captures that behavior.
4. Prefer `assertThrowsExactly` when the exception type is part of the contract, and verify the returned exception message with `assertEquals` when the message matters.
5. Use Mockito only for real collaboration boundaries and Awaitility only for genuinely asynchronous behavior.

## First Runnable Commands or Code Shape

Start with one failing JUnit 5 test:

```java
class RetryServiceTest {
    @Test
    void retriesThreeTimesBeforeFailing() {
        RetryException error = assertThrowsExactly(RetryException.class, () -> service.run());
        assertEquals("retry budget exhausted", error.getMessage());
    }
}
```
Use when: starting TDD or pinning a bug boundary before changing production code.

## Ready-to-Adapt Templates

Plain JUnit 5 test:

```java
class ProfileServiceTest {
    @Test
    void returnsCachedProfileWhenPresent() {
        assertEquals("user-1", service.loadProfile("user-1").id());
    }
}
```
Use when: the behavior is synchronous and local.

Mockito boundary:

```java
class ClientServiceTest {
    @Test
    void retriesAfterTransientFailure() {
        when(client.call())
                .thenThrow(new IOException("temporary"))
                .thenReturn("ok");
        assertEquals("ok", service.run());
        verify(client).call();
    }
}
```
Use when: the code under test talks to a real boundary such as a client, gateway, or repository.

Awaitility eventual assertion:

```java
class EventPublisherTest {
    @Test
    void publishesEventually() {
        service.triggerAsyncWork();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertTrue(repository.contains("done")));
    }
}
```
Use when: the behavior depends on background work or eventual consistency.

Nested JUnit 5 contexts:

```java
class CartServiceTest {
    @Nested
    class WhenCartIsEmpty {
        @Test
        void returnsZeroTotal() {
            assertEquals(BigDecimal.ZERO, service.total());
        }
    }
}
```
Use when: one API has a few clear scenario groups and nested contexts make the test surface easier to scan.

Declarative timeout:

```java
class FeedRefreshTest {
    @Test
    @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
    void refreshFinishesWithinBudget() {
        service.refresh();
    }
}
```
Use when: the contract includes an ordinary upper time bound and the test does not need preemptive interruption.

Maven and Gradle test wiring:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
</plugin>
```

```groovy
test {
    useJUnitPlatform()
}
```
Use when: the blocker is test execution wiring rather than test logic itself.

## Validate the Result

Validate the common case with these checks:

- the first failing test captures one observable behavior
- Mockito exists only where a real collaboration boundary needs isolation
- Awaitility appears only for asynchronous or eventually consistent behavior
- Maven uses Surefire for unit-style tests and Failsafe only when lifecycle separation truly matters
- Gradle uses `useJUnitPlatform()` for JUnit 5 execution

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| assertion style, lifecycle hooks, parameterized JUnit 5 detail, mocking boundaries, or Awaitility-based async verification | `./references/testing-core.md` |

## Invariants

- MUST write the smallest failing test that captures the requested behavior.
- MUST choose the correct scope: unit, integration, or contract.
- SHOULD prefer one observable behavior per test.
- MUST keep test names descriptive and scenario-based.
- SHOULD use JUnit 5 as the default baseline unless the repository already standardizes a different test runner.
- MUST introduce Mockito only where a real collaboration boundary needs isolation.
- MUST introduce Awaitility only for asynchronous or eventually consistent behavior.
- MUST separate build-tool setup guidance from behavioral test logic.
- MUST distinguish Surefire and Failsafe responsibilities in Maven projects.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| writing several behaviors into one test | failure meaning becomes vague | keep one observable behavior per test |
| mocking everything by default | tests stop reading like behavior and start reading like implementation trivia | mock only real boundaries |
| using Awaitility to hide deterministic bugs | waits mask design problems instead of testing async behavior | make the code deterministic where possible, then wait only for genuine eventual behavior |
| mixing build-tool setup with behavior assertions | the test intent gets buried in config detail | keep execution wiring separate from the behavioral explanation |

## Scope Boundaries

- Activate this skill for:
  - TDD sequencing
  - JUnit structure and assertion discipline
  - Maven or Gradle test execution guidance
- Do not use this skill as the primary source for:
  - dependency coordinate or release lookup guidance
  - public API or type-modeling decisions
  - Spring Boot test-slice selection or full context wiring
  - performance or concurrency tradeoffs
  - JVM incident triage from runtime evidence
