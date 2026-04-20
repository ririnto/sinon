---
name: java-test
description: >-
  Write JUnit 5 tests, follow TDD red-green-refactor in Java, fix failing tests,
  configure Maven Surefire or Gradle test execution, and choose the smallest correct test scope.
  Use when the user asks to write a JUnit test, follow TDD in Java,
  fix a failing Maven test, or needs guidance on Java test-first workflows.
---

# Java Test

Drive Java work through tests first, then implementation, then cleanup. The common case is writing the smallest failing JUnit 5 test, making the smallest production change, and keeping build-tool wiring separate from the test's behavioral intent.

## Operating rules

- MUST write the smallest failing test that captures the requested behavior.
- MUST choose the correct scope: unit, integration, or contract.
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

## Procedure

1. Read the target production code and the nearest related tests.
2. Identify the requested behavior or bug before touching implementation.
3. Write the smallest failing JUnit 5 test that captures that behavior.
4. Prefer `assertThrowsExactly` when the exception type is part of the contract, and verify the returned exception message with `assertEquals` when the message matters.
5. Use Mockito only for real collaboration boundaries and Awaitility only for genuinely asynchronous behavior.
6. Wire the build tool (Maven Surefire or Gradle `useJUnitPlatform()`) only when execution setup is the actual blocker.

## First runnable commands

Start with one failing JUnit 5 test:

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
        verify(client).call();
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
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertTrue(repository.contains("done")));
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

- If the main issue is dependency coordinate lookup or repository-wide build governance, that is outside this skill's scope.
- If the question is about public API or type-modeling decisions, that is outside this skill's scope.
- If the question is about Spring Boot test-slice selection or full context wiring, state that framework-specific test configuration is outside this skill's scope.
- If the code under test has no real collaboration boundaries, prefer ordinary objects over mocks.
- If the behavior can be made deterministic without waiting, make it deterministic before reaching for Awaitility.
- If using `assertTimeoutPreemptively`, warn that it runs work on a separate thread and may break `ThreadLocal`-sensitive code such as transaction-bound framework tests.

## Output contract

Return:

1. The failing test that captures one observable behavior.
2. The minimal production change that makes the test pass.
3. Build-tool wiring only if execution setup was the blocker.
4. Explicit note if Mockito or Awaitility usage is justified at a real boundary.

## Support-file pointers

| If the blocker is... | Open... |
| --- | --- |
| assertion style, lifecycle hooks, parameterized JUnit 5 detail, mocking boundaries, or Awaitility-based async verification | [`testing-core.md`](./references/testing-core.md) |

## Gotchas

- Do not write several behaviors into one test.
- Do not mock everything by default.
- Do not use Awaitility to hide deterministic bugs.
- Do not mix build-tool setup with behavior assertions.
