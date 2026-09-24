---
description: >-
  Open this when Kotest tests need Koin injection, a Testcontainer, WireMock, or MockServer.
---

# Service Integration Extensions

Use these modules only when the tested behavior needs their runtime boundary.
The examples assume a compatible Kotest BOM in `testImplementation` as shown in `gradle-dependencies-and-config.md`.
Verify that its selected version manages each module used here.
Declare only the modules needed by the test:

```kotlin
dependencies {
    testImplementation("io.kotest:kotest-extensions-koin")
    testImplementation("io.kotest:kotest-extensions-testcontainers")
    testImplementation("io.kotest:kotest-extensions-wiremock")
    testImplementation("io.kotest:kotest-extensions-mockserver")
}
```

These are alternatives, not a required bundle.
Koin's `KoinTest` needs `io.insert-koin:koin-test` on the test compile classpath.
Testcontainers' `GenericContainer` needs `org.testcontainers:testcontainers` directly because Kotest does not expose it on the consumer compile classpath.
Use the project's Koin and Testcontainers versions.
The Kotest BOM does not manage these external libraries.

## Koin

`KoinExtension` starts and stops Koin around each leaf test by default.
Use a module factory to rebuild modules for each start.
Reusing a mutated module can leak cached singletons.
Select `KoinLifecycleMode.Root` only when related leaf tests must share one Koin context.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.koin.KoinExtension
import io.kotest.matchers.shouldBe
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

class KoinInjectionTest : FunSpec(), KoinTest {
    init {
        extension(KoinExtension { listOf(module { single { "ready" } }) })
        test("resolves the configured value") {
            val value by inject<String>()
            value shouldBe "ready"
        }
    }
}
```

## Testcontainers

`install(TestContainerSpecExtension(...))` starts the container on installation and stops it after the spec.
Use `TestContainerProjectExtension` only when multiple specs intentionally share one container.
The module also has `JdbcDatabaseContainerSpecExtension` and its project variant, which return a `DataSource` backed by HikariCP.
Docker must be available for these integration tests.
The `redis:7-alpine` tag is a sample fixture, not an image upgrade recommendation.
Check the image registry and the application's Redis compatibility before selecting a tag.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.TestContainerSpecExtension
import io.kotest.matchers.booleans.shouldBeTrue
import org.testcontainers.containers.GenericContainer

class RedisContainerTest : FunSpec({
    val redis = install(TestContainerSpecExtension(GenericContainer<Nothing>("redis:7-alpine"))) {
        withExposedPorts(6379)
    }

    test("starts the Redis boundary") {
        redis.isRunning.shouldBeTrue()
    }
})
```

## WireMock

`WireMockListener` starts and stops a WireMock server per spec or per test.
Choose `PER_TEST` when tests change stubs, or `PER_SPEC` when a shared server is safe.
A dynamic port avoids collisions between local and CI runs.

```kotlin
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.wiremock.ListenerMode
import io.kotest.extensions.wiremock.WireMockListener
import io.kotest.matchers.shouldBe
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class WireMockHealthTest : FunSpec({
    val server = WireMockServer(WireMockConfiguration.options().dynamicPort())
    extension(WireMockListener(server, ListenerMode.PER_TEST))
    test("serves the configured response") {
        server.stubFor(WireMock.get(WireMock.urlEqualTo("/health"))
            .willReturn(WireMock.aResponse().withStatus(204)))
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:${server.port()}/health")).GET().build(),
            HttpResponse.BodyHandlers.discarding()
        ).statusCode() shouldBe 204
    }
})
```

## MockServer

`install(MockServerExtension())` starts a server with an allocated port and stops it after the spec.
Define expectations for each test.
A fixed port can collide with another local process.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.mockserver.MockServerExtension
import io.kotest.matchers.shouldBe
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest as MockRequest
import org.mockserver.model.HttpResponse as MockResponse

class MockServerHealthTest : FunSpec({
    val server = install(MockServerExtension())
    test("serves the configured response") {
        MockServerClient("localhost", server.port).`when`(
            MockRequest.request().withMethod("GET").withPath("/health")
        ).respond(MockResponse.response().withStatusCode(204))
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:${server.port}/health")).GET().build(),
            HttpResponse.BodyHandlers.discarding()
        ).statusCode() shouldBe 204
    }
})
```
