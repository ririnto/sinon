---
description: >-
  Open this when choosing or registering a Kotest extension or defining its lifecycle scope.
---

# Kotest Extensions

Kotest 6.2 extensions are reusable lifecycle hooks and engine interceptors.
Use spec hooks for local setup before adding an extension.
Register an extension in a spec when only that spec needs it, or in project configuration when every spec needs it.
Project-start listeners must be registered at project scope, since a spec starts too late.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.system.OverrideMode
import io.kotest.extensions.system.SystemPropertyTestListener
import io.kotest.matchers.booleans.shouldBeTrue

class FeatureFlagTest : FunSpec({
    extension(SystemPropertyTestListener(mapOf("feature.enabled" to "true"), OverrideMode.SetOrOverride))
    test("reads the enabled feature") {
        System.getProperty("feature.enabled").toBooleanStrict().shouldBeTrue()
    }
})
```

`SystemPropertyTestListener` is in `io.kotest:kotest-extensions-jvm`.
It snapshots all properties at construction, then restores that snapshot after each test.
`SetOrOverride` permits an existing key.
The default `SetOrError` fails if that key exists.
System properties are JVM-global, so concurrent specs using this listener can observe another spec's temporary value.
Disable parallel execution for those specs, or inject per-test configuration instead.
Use the Kotest BOM for the module's version.
`environment-and-time-extensions.md` covers JVM-global state and time controls.

## Lifecycle listeners

| Family | Runs when | Choose when |
| --- | --- | --- |
| `BeforeEachListener` / `AfterEachListener` | A leaf test starts / finishes. | Shared leaf fixture setup or cleanup is needed. |
| `BeforeContainerListener` / `AfterContainerListener` | A container starts / finishes. | Nested context lifecycle is the contract. |
| `BeforeTestListener` / `AfterTestListener` | Any test case, including a container, starts / finishes. | One hook must observe all cases. |
| `BeforeInvocationListener` / `AfterInvocationListener` | Each invocation of a test runs. | Repeated tests need per-invocation handling. |
| `BeforeSpecListener` / `AfterSpecListener` | Each spec instance starts / finishes. | Isolation may create multiple instances. |
| `PrepareSpecListener` / `FinalizeSpecListener` | A spec class starts / finishes once. | Work must not repeat per spec instance. |
| `BeforeProjectListener` / `AfterProjectListener` | A project starts / finishes once. | Global setup or teardown is needed. |

A skipped test does not run the normal test lifecycle callbacks.
After callbacks still run when a started test fails.
Under `InstancePerRoot`, spec-instance listeners can run more than once, while prepare/finalize listeners run once per class.

## Engine extensions

`TestCaseExtension` and `SpecExtension` intercept execution and can alter results or coroutine context.
`SpecRefExtension` intercepts before spec instantiation.
`ConstructorExtension` creates a spec instance.
`EnabledExtension` and `TagExtension` influence selection.
`DisplayNameFormatterExtension` changes reporting names.
`ProjectExtension` intercepts project execution, and `SpecExecutionOrderExtension` sorts specs.
`InstantiationListener`, `PostInstantiationExtension`, and `InstantiationErrorListener` cover spec creation.
`IgnoredSpecListener` and `IgnoredTestListener` cover skipped cases.
Use an engine extension only when a lifecycle hook or a published integration cannot express the behavior.

## Official integration modules

These are separate dependencies.
Add only the module required by the tested boundary.

| Boundary | Official module | Typical purpose |
| --- | --- | --- |
| Spring Test | `io.kotest:kotest-extensions-spring` | Application context, injection, transactions. |
| Koin | `io.kotest:kotest-extensions-koin` | Module lifecycle and injection. |
| Testcontainers | `io.kotest:kotest-extensions-testcontainers` | Container lifecycle. |
| WireMock | `io.kotest:kotest-extensions-wiremock` | HTTP stub server lifecycle. |
| MockServer | `io.kotest:kotest-extensions-mockserver` | Mock HTTP server lifecycle. |
| JVM environment | `io.kotest:kotest-extensions-jvm` | System property and standard-output listeners. |
| Controllable `java.time.Clock` | `io.kotest:kotest-extensions` | Inject `TestClock` instead of reading wall time. |
| Current time | `io.kotest:kotest-extensions-now` | Override the current instant when injection is unavailable. |
| Allure | `io.kotest:kotest-extensions-allure` | Emit Allure test results. |
| HTML reporting | `io.kotest:kotest-extensions-htmlreporter` | Write an HTML test report. |
| JUnit XML reporting | `io.kotest:kotest-extensions-junitxml` | Write XML for build systems. |
| BlockHound | `io.kotest:kotest-extensions-blockhound` | Detect blocking calls on nonblocking threads. |
| Decoroutinator | `io.kotest:kotest-extensions-decoroutinator` | Improve coroutine stack traces. |
| Pitest | `io.kotest:kotest-extensions-pitest` | Integrate mutation testing. |
| Ktor response assertions | `io.kotest:kotest-assertions-ktor` | Match Ktor client responses, not lifecycle events. |

For Koin, Testcontainers, WireMock, and MockServer setup, open `service-integration-extensions.md`.
For JVM system state, `TestClock`, and current-time control, open `environment-and-time-extensions.md`.
For reporting, diagnostics, Pitest, and Ktor matchers, open `tooling-extensions.md`.
For Spring injection and transaction lifecycle, open `spring-extension.md`.
`TestClock` and Ktor assertions are utilities, not listener registration.
Use the project's Kotest BOM for these modules.
Add non-Kotest dependencies only when an integration needs them.
