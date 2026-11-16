---
metadata:
  reference:
    JUnit:
      - version: 6.1.3
        url: https://docs.junit.org/6.1.3/user-guide/
      - version: 5.14.4
        url: https://docs.junit.org/5.14.4/user-guide/
    Mockito:
      url: https://site.mockito.org/
    Awaitility:
      url: https://github.com/awaitility/awaitility/wiki/Usage
name: java-test
description: >-
  Write or fix Java tests, choose test scope, and configure JUnit execution in Maven or Gradle.
---

# Java Test

Prove the requested behavior with the smallest suitable test in the repository's existing native test harness.
Use red-green-refactor when TDD is requested or a regression needs a reproducing test.
Keep build-tool wiring separate from behavioral assertions.

## Operating rules

- MUST select tests from acceptance criteria and regression risks, reusing existing coverage when sufficient.
- SHOULD prefer a unit test when it can prove the behavior.
- Use an integration test when the behavior depends on interactions among real components or an external boundary.
- MUST reserve end-to-end tests for distinct core user journeys that lower-level tests do not already prove.
- MUST NOT impose test-layer ratios or add every layer for each change.
- MUST use the repository's native test runner without adding a task-specific execution wrapper.
- MUST NOT test prose instructions, headings, wording, word counts, or declared file lists when review is sufficient.
- SHOULD prefer one observable behavior per test.
- MUST keep test names descriptive and scenario-based.
- SHOULD use the repository's managed JUnit version.
  For new projects, prefer the latest stable JUnit 6 when tests run on Java 17 or later.
  Use JUnit 5 when tests must run on Java 8 through 16 or the project manages that line.
- MUST introduce Mockito only where a real collaboration boundary needs isolation.
- MUST introduce Awaitility only for asynchronous or eventually consistent behavior.
- MUST separate build-tool setup guidance from behavioral test logic.
- MUST distinguish Surefire and Failsafe responsibilities in Maven projects.

### Code conventions

- Each file-oriented example keeps exactly one top-level root type.
- The root declaration is a type, not a top-level method or field.
- Use `@Nested` when one behavior splits cleanly by scenario context.
- Use `@Timeout` for declarative per-test time limits and `assertTimeoutPreemptively` only when preemptive interruption is actually required.
- Compare full serialized output after parsing structured formats into exact fields or elements.
  - Use containment only when membership itself is the observable contract.

## Task Context

Read the target production code and related tests to identify the observable contract and missing evidence.
Prefer `assertThrowsExactly` when the exception type is part of the contract (JUnit Jupiter 5.8 or later, or JUnit 6).
Verify the returned exception message with `assertEquals` when the message matters.
Change Maven Surefire or Gradle `useJUnitPlatform()` wiring only when execution setup is the blocker.
Use templates for the selected test shape, not as a checklist of tests to add.
Open [`testing-core.md`](./references/testing-core.md) for assertion, lifecycle, mocking, or async verification details.

## Regression Example

Adapt this JUnit Jupiter exception-contract example to the target fixture:

```java
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class RetryServiceTest {
    @Test
    void retriesThreeTimesBeforeFailing() {
        RetryException error = assertThrowsExactly(RetryException.class, () -> service.run());
        assertEquals("retry budget exhausted", error.getMessage());
    }
}
```

Use when: starting TDD or pinning a bug boundary before changing production code.

Use [test templates](./references/test-templates.md) when implementing JUnit scenarios, parameterization, mocking, async assertions, timeouts, temporary directories, or build-tool wiring.

## Edge cases

- Dependency coordinate lookup is outside this skill's scope.
  Use `java:java-dependency-versioning`.
- Public API or type-modeling decisions are outside this skill's scope.
  Use `java:java-language-design`.
- Spring Boot test-slice selection and full context wiring are outside this skill's scope.
- If the code under test has no real collaboration boundaries, prefer ordinary objects over mocks.
- If the behavior can be made deterministic without waiting, make it deterministic before reaching for Awaitility.
- If using `assertTimeoutPreemptively`, warn that it runs work on a separate thread and may break `ThreadLocal`-sensitive code such as transaction-bound framework tests.

## Completion

For test changes, run the affected native tests and fix failures caused by the change within scope.
Report the behavior covered, exact command and result, and any unverified boundary.
Do not stop after producing a failing test when the task also authorizes the fix.
