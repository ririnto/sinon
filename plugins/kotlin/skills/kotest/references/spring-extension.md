---
description: >-
  Open this when Kotest tests need Spring context injection, lifecycle callbacks, or transactions.
---

# Spring Extension

Use the Spring Test context only when the behavior depends on real wiring or transaction management.
The Kotest BOM from `gradle-dependencies-and-config.md` manages `kotest-extensions-spring`.
Spring and H2 versions come from the project's Spring Boot dependency management or its Spring BOM and version catalog.

For a plain Spring context, declare the required Spring libraries:

```kotlin
dependencies {
    testImplementation("io.kotest:kotest-extensions-spring")
    testImplementation("org.springframework:spring-test")
    testImplementation("org.springframework:spring-context")
}
```

The transaction example also needs these plain-Spring dependencies:

```kotlin
dependencies {
    testImplementation("org.springframework:spring-jdbc")
    testImplementation("org.springframework:spring-tx")
    testRuntimeOnly("com.h2database:h2")
}
```

For Spring Boot, use the project's managed starters instead of the plain-Spring setup:

```kotlin
dependencies {
    testImplementation("io.kotest:kotest-extensions-spring")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testRuntimeOnly("com.h2database:h2")
}
```

The JDBC starter and H2 are needed only by the transaction example, not by the Boot bean-injection example.
Do not add every optional dependency to a plain unit test.

## Activate and inject

Register `SpringExtension` per spec with `@ApplyExtension`, or project-wide in `AbstractProjectConfig.extensions`.
The Kotest annotation activates the integration.
Spring's `@ContextConfiguration` selects the bean configuration.
With the extension enabled, Spring can inject a bean into the spec's primary constructor.

`TestComponents.kt` (fixture):

```kotlin
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class TestComponents {
    @Bean
    fun greetingService(): GreetingService = GreetingService()

    class GreetingService {
        fun greet(name: String): String = "hello, $name"
    }
}
```

`GreetingSpringTest.kt`:

```kotlin
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.test.context.ContextConfiguration

@ApplyExtension(SpringExtension::class)
@ContextConfiguration(classes = [TestComponents::class])
open class GreetingSpringTest(service: TestComponents.GreetingService) : FunSpec({
    test("uses an injected bean") {
        service.greet("system") shouldBe "hello, system"
    }
})
```

The `open` spec lets Spring process class-level annotations that need proxying.
Project-wide registration replaces the Kotest annotation, not Spring's context configuration:

```kotlin
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.spring.SpringExtension

class ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(SpringExtension())
}
```

## Lifecycle and transactions

By default Spring test callbacks fire at each leaf test.
Use `SpringExtension(SpringTestLifecycleMode.Root)` in project config for callbacks at each root test.
For a per-spec annotation in root mode, use `@ApplyExtension(SpringRootTestExtension::class)`.
Kotest guarantees callback-group order but not the order of callbacks within one group.

A test-managed transaction needs a `PlatformTransactionManager` bean and a data source shared with JDBC operations.
The following fixture creates an isolated H2 database and table before the test transaction begins.

`TransactionComponents.kt` (fixture):

```kotlin
import javax.sql.DataSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType

@Configuration(proxyBeanMethods = false)
class TransactionComponents {
    @Bean
    fun dataSource(): DataSource = EmbeddedDatabaseBuilder()
        .generateUniqueName(true)
        .setType(EmbeddedDatabaseType.H2)
        .build()

    @Bean
    fun transactionManager(dataSource: DataSource): DataSourceTransactionManager = DataSourceTransactionManager(dataSource)

    @Bean
    fun jdbcTemplate(dataSource: DataSource): JdbcTemplate = JdbcTemplate(dataSource).also { jdbc ->
        jdbc.execute("CREATE TABLE accounts (id VARCHAR(64) PRIMARY KEY)")
    }
}
```

`AccountTransactionTest.kt`:

```kotlin
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.annotation.Transactional

@Transactional
@ContextConfiguration(classes = [TransactionComponents::class])
@ApplyExtension(SpringExtension::class)
open class AccountTransactionTest(jdbc: JdbcTemplate) : FunSpec({
    test("reads an account within its transaction") {
        jdbc.update("INSERT INTO accounts (id) VALUES (?)", "a-1")
        jdbc.queryForObject("SELECT id FROM accounts WHERE id = ?", String::class.java, "a-1") shouldBe "a-1"
    }
})
```

Spring rolls back each leaf test's transaction by default.
One test's write is not a fixture for another.
Use `@Commit` only when persistence after the test is itself required.
Keep JDBC operations on the test's transaction thread.
A preemptive timeout or another thread can bypass rollback.

## Spring Boot context

`@SpringBootTest` loads an application context through Spring Boot rather than a plain Spring configuration.
Supply an explicit test application when the test must not depend on package-scanning an existing application.
The project's Boot starter-test and the Kotest Spring extension are required.
This example needs no database starter.

`BootTestApplication.kt` (fixture):

```kotlin
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
class BootTestApplication {
    @Bean
    fun checkoutService(): CheckoutService = CheckoutService()

    class CheckoutService {
        fun quote(cartId: String): String = "ready:$cartId"
    }
}
```

`CheckoutBootTest.kt`:

```kotlin
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(classes = [BootTestApplication::class])
@ApplyExtension(SpringExtension::class)
open class CheckoutBootTest(service: BootTestApplication.CheckoutService) : FunSpec({
    test("uses the Boot-configured bean") {
        service.quote("cart-1") shouldBe "ready:cart-1"
    }
})
```

Kotest still needs `@ApplyExtension` or project registration for constructor injection.
The extension exposes Spring's `TestContextManager` through `testContextManager()` only when direct context inspection is necessary.
