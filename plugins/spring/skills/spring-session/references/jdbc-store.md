# Spring Session JDBC store

Open this reference when the session store is relational and table naming, cleanup cadence, transaction control, or JSON attribute storage must be customized.

Choose JDBC-backed sessions when the application already depends on a relational database and SQL-oriented operations are an acceptable trade-off against Redis latency.

## JDBC baseline

```xml
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-jdbc</artifactId>
</dependency>
```

## Repository customization

```java
@Bean
SessionRepositoryCustomizer<JdbcIndexedSessionRepository> jdbcRepositoryCustomizer(
        TransactionTemplate transactionTemplate) {
    return repository -> {
        repository.setTableName("SPRING_SESSION");
        repository.setDefaultMaxInactiveInterval(Duration.ofMinutes(45));
        repository.setTransactionOperations(transactionTemplate);
    };
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Database naming rules require a custom table | `setTableName(...)` |
| Cleanup cadence must differ from the default | customize the cleanup job or schedule |
| The application uses multiple data sources or explicit propagation | set dedicated transaction operations |
| Session attributes must live in JSON-capable columns | add JDBC JSON serialization and conversion explicitly |

## Gotchas

- Do not choose JDBC casually for very high session churn when Redis latency and TTL semantics are a better fit.
- Do not rely on default cleanup cadence without confirming that expired rows disappear at an acceptable rate.
- Do not assume transaction behavior is correct when Spring Session shares infrastructure with unrelated database work.
