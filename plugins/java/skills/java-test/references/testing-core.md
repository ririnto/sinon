---
title: Java Testing Core Reference
description: >-
  Reference for JUnit 5 building blocks, assertion patterns,
  Mockito usage, Awaitility async verification,
  and representative test shapes for Java TDD workflows.
---

Open this reference when the common Java test shape is already clear and the remaining blocker is assertion detail, mocking, or asynchronous verification.

Official references:

- JUnit 5 user guide: <https://junit.org/junit5/docs/current/user-guide/>
- Official Mockito site: <https://site.mockito.org/>
- Official Awaitility wiki: <https://github.com/awaitility/awaitility/wiki/Usage>

## JUnit 5 building blocks

- Core test annotations: `@Test`, `@DisplayName`, `@Nested`, `@Tag`, `@Disabled`
- Lifecycle annotations: `@BeforeEach`, `@AfterEach`, `@BeforeAll`, `@AfterAll`
- Assertion methods: `assertEquals`, `assertNotEquals`, `assertTrue`, `assertFalse`, `assertNull`, `assertNotNull`, `assertSame`, `assertNotSame`, `assertThrowsExactly`, `assertDoesNotThrow`, `assertAll`, `assertArrayEquals`, `assertIterableEquals`, `assertLinesMatch`, `assertTimeoutPreemptively`
- Parameterized test entry points: `@ParameterizedTest`, `@ValueSource`, `@MethodSource`, `@CsvSource`, `@EnumSource`, `@NullSource`, `@EmptySource`

## Representative JUnit 5 patterns

Prefer `assertThrowsExactly` when the exact exception type is part of the contract.

When the exception message is part of the contract, capture the returned exception and verify `getMessage()` with `assertEquals`:

```java
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class RetryServiceTest {
    @Test
    void rejectsInvalidRetryBudget() {
        RetryException error = assertThrowsExactly(RetryException.class, () -> service.run());
        assertEquals("retry budget exhausted", error.getMessage());
    }
}
```

Use `assertAll` when several related assertions describe one observable behavior:

```java
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderSnapshotTest {
    @Test
    void returnsCompleteOrderSnapshot() {
        OrderSnapshot snapshot = service.load(orderId);
        assertAll("order snapshot",
                () -> assertEquals(orderId, snapshot.id()),
                () -> assertEquals("SHIPPED", snapshot.status()),
                () -> assertEquals(3, snapshot.lineCount()));
    }
}
```

Use `assertDoesNotThrow` when the contract requires that no exception occurs:

```java
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ConfigurationLoaderTest {
    @Test
    void loadsValidConfigWithoutError() {
        assertDoesNotThrow(() -> loader.load("valid-config.yaml"));
    }
}
```

Use `@Nested` to group scenario-specific tests under one outer behavior without flattening everything into long method names:

```java
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentPolicyTest {
    @Nested
    class WhenLimitIsExceeded {
        @Test
        void rejectsTheCharge() {
            assertTrue(policy.rejects(limitBreakingCharge));
        }
    }
}
```

Use `@Timeout` for declarative limits on one test or a whole test class:

```java
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ReportExportTest {
    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void exportsWithinBudget() {
        service.export(reportId);
    }
}
```

Use `assertTimeoutPreemptively` only when the test must abort work on timeout rather than merely report a slow path:

```java
import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class PricingClientTest {
    @Test
    void abortsSlowRemoteCall() {
        assertTimeoutPreemptively(Duration.ofMillis(200), () -> service.refreshPrices());
    }
}
```

`assertTimeoutPreemptively` runs the work on a different thread, so do not use it casually around `ThreadLocal`-sensitive code such as transaction-bound framework tests.

Use `@ParameterizedTest` for the same rule over multiple inputs, not for unrelated scenarios.

Use `@BeforeEach` for small fixture setup and keep shared mutable state to a minimum.

## Mockito guidance

- Mock creation: `mock(...)`, `@Mock`, `@Spy`
- Stubbing: `when(...).thenReturn(...)`, `thenThrow(...)`, `thenAnswer(...)`, `doThrow(...)`, `doReturn(...)`
- Verification: `verify(...)`, `verifyNoInteractions(...)`, `verifyNoMoreInteractions(...)`
- Argument matching: `any()`, `anyString()`, `eq(...)`, `argThat(...)`
- Argument capture: `ArgumentCaptor.forClass(...)`, `@Captor`

Default approach:

1. Prefer ordinary objects over mocks when the dependency is simple enough to instantiate directly.
2. Stub only behavior that matters for the scenario.
3. Verify observable collaboration after execution, not before.
4. Avoid over-specifying call counts unless they are part of the behavior under test.

Representative Mockito shape with `@ExtendWith`:

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

Spy for partial mocking of real objects:

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {
    @Spy
    private AuditService service = new AuditService();

    @Test
    void skipsSlowValidationWhenCached() {
        doReturn(true).when(service).isCached("report-1");
        service.process("report-1");
        verify(service).isCached("report-1");
    }
}
```

## Awaitility guidance

- Entry points: `await()`, `until(...)`, `untilAsserted(...)`
- Timeout controls: `atMost(...)`, `atLeast(...)`
- Polling controls: `pollInterval(...)`, `pollDelay(...)`

Default approach:

1. First check whether the code can be made deterministic without waiting.
2. Use `untilAsserted(...)` when the target state is easiest to express as ordinary assertions.
3. Keep time bounds explicit so test failures explain whether the behavior was too slow or never happened.
4. Do not use Awaitility to paper over deterministic bugs, race conditions, or missing synchronization design.

Representative Awaitility shape:

```java
import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JobMonitorTest {
    @Test
    void marksJobCompletedEventually() {
        service.start(jobId);

        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertEquals(Status.COMPLETED, repository.status(jobId)));
    }
}
```
