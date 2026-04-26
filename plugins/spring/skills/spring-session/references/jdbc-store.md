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

## Application properties

```yaml
spring:
  session:
    store-type: jdbc
    timeout: 45m
    jdbc:
      table-name: SPRING_SESSION
  datasource:
    url: jdbc:postgresql://localhost:5432/sessions
    driver-class-name: org.postgresql.Driver
    username: app_user
    password: secret
```

## Repository customization

```java
@Bean
SessionRepositoryCustomizer<JdbcIndexedSessionRepository> jdbcRepositoryCustomizer() {
    return repository -> {
        repository.setTableName("SPRING_SESSION");
        repository.setDefaultMaxInactiveInterval(Duration.ofMinutes(45));
    };
}
```

Use a dedicated transaction-operations bean when Spring Session JDBC needs different propagation or a different transaction manager:

```java
@Bean("springSessionTransactionOperations")
TransactionOperations springSessionTransactionOperations(PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return transactionTemplate;
}
```

## Test example

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class JdbcSessionFlowTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void sessionStateIsReusedAcrossRequests() throws Exception {
        MvcResult first = mockMvc.perform(post("/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-1\"}"))
            .andExpect(status().isOk())
            .andReturn();
        Cookie sessionCookie = first.getResponse().getCookie("SESSION");
        ResultActions secondRequest = mockMvc.perform(post("/cart/items")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-2\"}"));
        assertAll(
            () -> secondRequest.andExpect(status().isOk()),
            () -> secondRequest.andExpect(jsonPath("$.itemCount").value(2))
        );
    }
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
