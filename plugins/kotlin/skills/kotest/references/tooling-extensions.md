---
description: >-
  Open this when Kotest needs test reports, blocking-call detection, stack traces, mutation testing, or Ktor response matchers.
---

# Tooling Extensions

Use a compatible Kotest BOM from `gradle-dependencies-and-config.md` for these modules.
Verify that its selected version manages each module used here.
Add only the modules for the behavior under test or the report your build consumes.

## JUnit XML and HTML reports

Use Gradle's built-in XML and HTML reports for Kotest JVM suites.
They are enabled by default for `Test` tasks.
Keep existing report configuration unless the build's report contract requires a change.
Kotest deprecated `JunitXmlReporter` in 6.2 and schedules its removal in 7.0.
Use the native reports for new configuration and migrate the deprecated reporter when updating an existing suite.

```kotlin
tasks.test {
    useJUnitPlatform()
}
```

The default `test` task writes XML under `build/test-results/test` and HTML under `build/reports/tests/test`.
The XML format is an output contract, not a requirement to write tests with JUnit APIs.
No separate Kotest reporting dependency or project extension is needed for these outputs.

## Allure

`AllureTestReporter` writes raw Allure results.
An Allure binary or build plugin renders the final report.
Register the reporter project-wide so all specs contribute results.

```kotlin
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.extensions.allure.AllureTestReporter

class ProjectConfig : AbstractProjectConfig() {
    override val extensions: List<Extension> = listOf(AllureTestReporter())
}
```

```kotlin
dependencies {
    testImplementation(libs.kotest.extensions.allure)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    systemProperty("allure.results.directory", layout.buildDirectory.dir("allure-results").get().asFile.absolutePath)
}
```

If the Allure Gradle plugin already sets the results directory, do not set it again.
Report generation needs the project's configured Allure tool.
The Kotest extension only collects data.

## Blocking calls and coroutine stacks

`BlockHound` detects blocking calls on nonblocking coroutine threads.
Register it per spec with `extension(BlockHound())` or project-wide.
Its default mode fails a detected call.
Use `BlockHoundMode.PRINT` only for diagnostics, since it does not fail the test.
`DecoroutinatorExtension` removes coroutine runtime frames from failed-test stack traces.
Register it project-wide so its project-start hook runs before specs execute.

```kotlin
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.extensions.blockhound.BlockHound
import io.kotest.extensions.decoroutinator.DecoroutinatorExtension

class ProjectConfig : AbstractProjectConfig() {
    override val extensions: List<Extension> = listOf(BlockHound(), DecoroutinatorExtension())
}
```

```kotlin
dependencies {
    testImplementation(libs.kotest.extensions.blockhound)
    testImplementation(libs.kotest.extensions.decoroutinator)
}
```

Use `withBlockHoundMode(BlockHoundMode.DISABLED) { ... }` only for a known blocking segment that cannot be changed.
Prefer moving legitimate blocking I/O to `Dispatchers.IO` instead of suppressing detection.
These modules are independent.
Use the combined example when both diagnostics are needed.

## Pitest mutation testing

The Pitest extension integrates Kotest's tests with a separately configured Pitest build plugin.
For Gradle, use the plugin's `pitest` configuration, not the ordinary `testImplementation` configuration.
The Kotest BOM must also be imported into that configuration for a versionless extension dependency.
This sample uses the 6.2.5 baseline.
Match the selected Kotest BOM version in both `testImplementation` and `pitest`.

```kotlin
dependencies {
    pitest(platform(libs.kotest.bom))
    pitest(libs.kotest.extensions.pitest)
}
```

Configure the project's Pitest Gradle plugin and mutation targets before running its `pitest` task.
With Pitest 1.6.7 or later, the extension on the Pitest classpath selects the Kotest test plugin without an explicit `testPlugin` setting.
Do not add the Pitest extension to every test suite when mutation testing is not configured.

## Ktor response matchers

`kotest-assertions-ktor` is a matcher module, not a lifecycle extension.
Kotest 6.2.5 source provides matchers for the Ktor client `HttpResponse`.
Keep the project's Ktor server-test dependencies and version under its existing dependency management.

```kotlin
import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

class HealthRouteTest : FunSpec({
    test("responds with a ready status") {
        testApplication {
            routing {
                get("/health") { call.respondText("ready", status = HttpStatusCode.OK) }
            }
            client.get("/health").apply {
                shouldHaveStatus(HttpStatusCode.OK)
                bodyAsText() shouldBe "ready"
            }
        }
    }
})
```

```kotlin
dependencies {
    testImplementation(libs.kotest.assertions.ktor)
}
```

The published 6.2.5 matcher source supports the Ktor client `HttpResponse`.
Use the client matcher shown here instead of older `TestApplicationResponse` examples.
