---
description: >-
  Open this when Kotest needs test reports, blocking-call detection, stack traces, mutation testing, or Ktor response matchers.
---

# Tooling Extensions

Use a compatible Kotest BOM from `gradle-dependencies-and-config.md` for these modules.
Verify that its selected version manages each module used here.
Add only the modules for the behavior under test or the report your build consumes.

## JUnit XML and HTML reports

Kotest's `JunitXmlReporter` can omit container-only cases and include the full nested test path.
`HtmlReporter` reads the XML produced under `test-results/test`.
Register both reporters for nested HTML output.

```kotlin
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.htmlreporter.HtmlReporter
import io.kotest.extensions.junitxml.JunitXmlReporter

class ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(
        JunitXmlReporter(includeContainers = false, useTestPathAsName = true),
        HtmlReporter()
    )
}
```

```kotlin
dependencies {
    testImplementation("io.kotest:kotest-extensions-junitxml")
    testImplementation("io.kotest:kotest-extensions-htmlreporter")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    reports {
        junitXml.required.set(false)
        html.required.set(false)
    }
    systemProperty("gradle.build.dir", layout.buildDirectory.get().asFile.absolutePath)
}
```

The Kotlin DSL `Test` type is `org.gradle.api.tasks.testing.Test`.
Do not disable Gradle's reports unless the Kotest reporters replace both outputs.
`HtmlReporter` defaults to `build/reports/tests/test`.
`JunitXmlReporter` defaults to `build/test-results/test` when `gradle.build.dir` is set.

## Allure

`AllureTestReporter` writes raw Allure results.
An Allure binary or build plugin renders the final report.
Register the reporter project-wide so all specs contribute results.

```kotlin
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.allure.AllureTestReporter

class ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(AllureTestReporter())
}
```

```kotlin
dependencies {
    testImplementation("io.kotest:kotest-extensions-allure")
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
import io.kotest.extensions.blockhound.BlockHound
import io.kotest.extensions.decoroutinator.DecoroutinatorExtension

class ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(BlockHound(), DecoroutinatorExtension())
}
```

```kotlin
dependencies {
    testImplementation("io.kotest:kotest-extensions-blockhound")
    testImplementation("io.kotest:kotest-extensions-decoroutinator")
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
    pitest(platform("io.kotest:kotest-bom:6.2.5"))
    pitest("io.kotest:kotest-extensions-pitest")
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
    testImplementation("io.kotest:kotest-assertions-ktor")
}
```

The published 6.2.5 matcher source supports the Ktor client `HttpResponse`.
Use the client matcher shown here instead of older `TestApplicationResponse` examples.
