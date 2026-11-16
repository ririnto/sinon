# Resilience, JDBC, and scheduling

## Resilience

Spring Framework 7.0 includes built-in retry support in `spring-core`, replacing standalone Spring Retry for most use cases.
Enable with `@EnableResilientMethods`:

```java
@Configuration
@EnableResilientMethods
class ResilienceConfig {
}
```

### `@Retryable`

```java
@Service
class OrderClient {
    @Retryable(maxRetries = 3, delay = 1000, multiplier = 2, maxDelay = 10000)
    Order fetchOrder(Long id) {
        return remoteService.getOrder(id);
    }
}
```

`@Retryable` supports imperative and reactive methods, with `maxRetries`, `delay`, `jitter`, `multiplier`, `maxDelay`, `includes`, `excludes`, and `predicate` available for retry policy configuration.

`RetryTemplate` is a separate programmatic API for callers that need to construct and invoke a retry policy directly:

```java
RetryPolicy policy = RetryPolicy.builder()
    .maxRetries(3)
    .delay(Duration.ofSeconds(1))
    .multiplier(2)
    .maxDelay(Duration.ofSeconds(10))
    .build();
RetryTemplate retry = new RetryTemplate(policy);
Order result = retry.invoke(() -> remoteService.getOrder(id));
```

### `@ConcurrencyLimit`

Throttle concurrent method invocations:

```java
@Service
class ResourceConsumer {
    @ConcurrencyLimit(5)
    void process(Resource resource) {
    }
}
```

Use `@Retryable` on methods that call external services prone to transient failure.
Use `@ConcurrencyLimit` when a shared resource has a hard concurrency ceiling.
Combine both annotations on the same method when the application needs both retry and throttling.

## Plain JDBC and DataSource

### DataSource registration

```java
@Configuration
class DataConfig {
    @Bean
    DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.hsqldb.jdbcDriver");
        dataSource.setUrl("jdbc:hsqldb:hsql://localhost/test");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
```

Use `DriverManagerDataSource` or `SimpleDriverDataSource` only for testing and stand-alone environments.
Pair plain Spring JDBC with a real pool such as HikariCP or Apache DBCP2 when the application manages its own production data source.

### JdbcTemplate wiring

```java
@Configuration
class DataConfig {
    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
```

Query a single row:

```java
@Service
class InventoryRepository {
    private final JdbcTemplate jdbc;

    InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Item findById(Long id) {
        return jdbc.queryForObject("SELECT id, name, quantity FROM items WHERE id = ?", (rs, rowNum) -> new Item(rs.getLong("id"), rs.getString("name"), rs.getInt("quantity")), id);
    }
}
```

Update with parameters:

```java
void updateQuantity(Long id, int quantity) {
    jdbc.update("UPDATE items SET quantity = ? WHERE id = ?", quantity, id);
}
```

Batch operations:

```java
void insertBatch(List<Item> items) {
    jdbc.batchUpdate("INSERT INTO items (name, quantity) VALUES (?, ?)", items.stream().map(item -> new Object[]{item.name(), item.quantity()}).toList());
}
```

Keep JDBC templates as the data-access primitive when the application needs plain SQL without an ORM layer.
Use `NamedParameterJdbcTemplate` when named parameters improve readability over positional `?` placeholders.
Use `JdbcClient` (from Spring Framework 6.1+) for a fluent alternative that supports statement-level settings such as fetch size, max rows, and query timeout:

```java
List<Item> items = jdbcClient.sql("SELECT id, name, quantity FROM items")
    .withFetchSize(500)
    .query((rs, rowNum) -> new Item(rs.getLong("id"), rs.getString("name"), rs.getInt("quantity")))
    .list();
```

Open [references/plain-jdbc-wiring.md](plain-jdbc-wiring.md) when the task needs transaction-scoped connections, `SqlRowSet`, `RowMapper` reuse, or `DataSourceTransactionManager` with plain JDBC.

## Async and scheduling

### Enable async processing

```java
@Configuration
@EnableAsync
class AppConfig {
    @Bean
    TaskExecutor taskExecutor() {
        return new SimpleAsyncTaskExecutor();
    }
}
```

Async method:

```java
@Component
class InventoryNotifier {
    @Async
    void sendAlert(String itemId) {
    }
}
```

Use `@EnableAsync` to activate framework-managed async execution.
The default `SimpleAsyncTaskExecutor` creates a new thread per call.
Register a custom `TaskExecutor` bean when pooled thread behavior, queue depth, or naming strategy matters.

### Enable scheduling

```java
@Configuration
@EnableScheduling
class AppConfig {
}
```

Scheduled method:

```java
@Component
class InventoryCleanup {
    @Scheduled(cron = "0 0 2 * * ?")
    void cleanup() {
    }
}
```

Use `@EnableScheduling` to activate framework-scheduled tasks.
Keep scheduled jobs idempotent and document the cron expression.

### Executor registration pointers

| Executor type | Use when |
| --- | --- |
| `SimpleAsyncTaskExecutor` | default async; each call gets a new thread |
| `ThreadPoolTaskExecutor` | pooled threads with queue; most common choice |
| `ThreadPoolTaskScheduler` | for `@Scheduled` methods that need a dedicated scheduler pool |
| `TaskExecutor` interface | `execute(Runnable)` abstraction for swapping executor implementations |

```java
@Bean
TaskExecutor threadPoolTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("inventory-");
    executor.initialize();
    return executor;
}
```

Open [references/async-executor-registration.md](async-executor-registration.md) when the task needs async exception handling, completion coordination with `Future` / `CompletableFuture`, or custom `TaskDecorator` for ThreadLocal propagation.

Do not stack `@Async` and `@Scheduled` on the same method casually.
Treat that combination as a proxy and executor design decision rather than ordinary scheduling.
Separate concerns into distinct methods when both behaviors are needed.
