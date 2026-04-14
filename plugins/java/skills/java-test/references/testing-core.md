---
title: Java Testing Core Reference
description: >-
  Reference for JUnit 5 detail, mocking boundaries, and asynchronous verification in Java tests.
---

Official user guide: <https://junit.org/junit5/docs/current/user-guide/>

Official Mockito site: <https://site.mockito.org/>

Official Awaitility wiki: <https://github.com/awaitility/awaitility/wiki/Usage>

Use this reference when the common Java test shape is already clear and the remaining blocker is assertion detail, mocking, or asynchronous verification.

## JUnit 5 Building Blocks

- Core test annotations: `@Test`, `@DisplayName`, `@Nested`, `@Tag`, `@Disabled`
- Lifecycle annotations: `@BeforeEach`, `@AfterEach`, `@BeforeAll`, `@AfterAll`
- Assertion methods to know first: `assertEquals`, `assertTrue`, `assertFalse`, `assertThrowsExactly`, `assertAll`
- Parameterized test entry points: `@ParameterizedTest`, `@ValueSource`, `@MethodSource`

## Representative JUnit 5 Patterns

- Prefer `assertThrowsExactly` when the exact exception type is part of the contract.
- When the exception message is part of the contract, capture the returned exception and verify `getMessage()` with `assertEquals`.

```java
class RetryServiceTest {
    @Test
    void rejectsInvalidRetryBudget() {
        RetryException error = assertThrowsExactly(RetryException.class, () -> service.run());
        assertEquals("retry budget exhausted", error.getMessage());
    }
}
```

- Use `assertAll` when several related assertions describe one observable behavior.

```java
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

- Use `@Nested` to group scenario-specific tests under one outer behavior without flattening everything into long method names.

```java
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

- Use `@Timeout` for declarative limits on one test or a whole test class.

```java
class ReportExportTest {
    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void exportsWithinBudget() {
        service.export(reportId);
    }
}
```

- Use `assertTimeoutPreemptively` only when the test must abort work on timeout rather than merely report a slow path.

```java
class PricingClientTest {
    @Test
    void abortsSlowRemoteCall() {
        assertTimeoutPreemptively(Duration.ofMillis(200), () -> service.refreshPrices());
    }
}
```

`assertTimeoutPreemptively` runs the work on a different thread, so do not use it casually around `ThreadLocal`-sensitive code such as transaction-bound framework tests.

- Use `@ParameterizedTest` for the same rule over multiple inputs, not for unrelated scenarios.
- Use `@BeforeEach` for small fixture setup and keep shared mutable state to a minimum.

## Mockito Guidance

- Mock creation: `mock(...)`, `@Mock`, `@Spy`
- Stubbing: `when(...).thenReturn(...)`, `thenThrow(...)`, `thenAnswer(...)`
- Verification: `verify(...)`, `verifyNoInteractions(...)`, `verifyNoMoreInteractions(...)`
- Argument matching: `any()`, `anyString()`, `eq(...)`, `argThat(...)`

Default approach:

1. Prefer ordinary objects over mocks when the dependency is simple enough to instantiate directly.
2. Stub only behavior that matters for the scenario.
3. Verify observable collaboration after execution, not before.
4. Avoid over-specifying call counts unless they are part of the behavior under test.

Representative Mockito shape:

```java
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

## Awaitility Guidance

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
class JobMonitorTest {
    @Test
    void marksJobCompletedEventually() {
        service.start(jobId);

        await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertEquals(Status.COMPLETED, repository.status(jobId)));
    }
}
```
