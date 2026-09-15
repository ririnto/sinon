---
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
- MUST use an integration test only when the behavior requires a real process, database, network, filesystem boundary, container, or framework runtime.
- MUST reserve end-to-end tests for distinct core user journeys that lower-level tests do not already prove.
- MUST NOT impose test-layer ratios or add every layer for each change.
- MUST use the repository's native test runner without adding a task-specific execution wrapper.
- MUST NOT test prose instructions, headings, wording, word counts, or declared file lists when review is sufficient.
- SHOULD prefer one observable behavior per test.
- MUST keep test names descriptive and scenario-based.
- SHOULD use JUnit 5 as the default baseline unless the repository already standardizes a different test runner.
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
Prefer `assertThrowsExactly` when the exception type is part of the contract (JUnit Jupiter 5.8 or later).
Verify the returned exception message with `assertEquals` when the message matters.
Change Maven Surefire or Gradle `useJUnitPlatform()` wiring only when execution setup is the blocker.
Use the templates below for the selected test shape, not as a checklist of tests to add.
Open [`testing-core.md`](./references/testing-core.md) for assertion, lifecycle, mocking, or async verification details.

## Regression Example

Adapt this JUnit 5 exception-contract example to the target fixture:

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

## Ready-to-adapt templates

### Plain JUnit 5 test

```java
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileServiceTest {
    @Test
    void returnsCachedProfileWhenPresent() {
        assertEquals("user-1", service.loadProfile("user-1").id());
    }
}
```

### Parameterized test

`@CsvSource` for tabular inputs:

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscountCalculatorTest {
    @ParameterizedTest
    @CsvSource({
        "100, 10, 90",
        "200, 25, 150",
        "50,  0,  50"
    })
    void appliesDiscountCorrectly(int price, int percent, int expected) {
        assertEquals(expected, calculator.apply(price, percent));
    }
}
```

`@MethodSource` for complex objects:

```java
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TierClassifierTest {
    static Stream<Arguments> tiers() {
        return Stream.of(
            Arguments.of(0, "free"),
            Arguments.of(999, "free"),
            Arguments.of(1000, "standard"),
            Arguments.of(9999, "standard")
        );
    }

    @ParameterizedTest
    @MethodSource("tiers")
    void classifiesCorrectly(int points, String expectedTier) {
        assertEquals(expectedTier, classifier.classify(points));
    }
}
```

### Mockito boundary

```java
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {
    @Mock
    private RemoteClient client;

    @InjectMocks
    private ClientService service;

    @Test
    void retriesAfterTransientFailure() throws IOException {
        when(client.call())
            .thenThrow(new IOException("temporary"))
            .thenReturn("ok");
        assertEquals("ok", service.run());
        verify(client, times(2)).call();
    }
}
```

Argument capture:

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock
    private MessageSender sender;

    @Captor
    private ArgumentCaptor<Message> messageCaptor;

    @Test
    void sendsFormattedMessage() {
        service.notify("alice", "welcome");
        verify(sender).send(messageCaptor.capture());
        assertEquals("welcome", messageCaptor.getValue().body());
    }
}
```

For additional Mockito features, see [`testing-core.md`](./references/testing-core.md).

### Awaitility eventual assertion

```java
import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPublisherTest {
    @Test
    void publishesEventually() {
        service.triggerAsyncWork();
        await().atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertTrue(repository.contains("done")));
    }
}
```

### Nested JUnit 5 contexts

```java
import java.math.BigDecimal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

### Declarative timeout

```java
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class FeedRefreshTest {
    @Test
    @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
    void refreshFinishesWithinBudget() {
        service.refresh();
    }
}
```

### Temporary directory

```java
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FileExporterTest {
    @Test
    void writesOutputFile(@TempDir Path tempDir) {
        Path output = tempDir.resolve("report.csv");
        exporter.exportTo(output);
        assertTrue(output.toFile().exists());
    }
}
```

### Build-tool wiring

Maven Surefire (unit tests, `*Test.java`):

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
</plugin>
```

Maven Failsafe (integration tests, `*IT.java`):

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
</plugin>
```

Gradle Groovy DSL:

```groovy
test {
    useJUnitPlatform()
}
```

Gradle Kotlin DSL:

```kotlin
tasks.test {
    useJUnitPlatform()
}
```

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
