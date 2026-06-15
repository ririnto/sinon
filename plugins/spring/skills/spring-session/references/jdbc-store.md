# JDBC session store

Open reference when the session store is relational and table naming, cleanup cadence, transaction control, JSON attribute storage, or multi-datasource routing must be customized.

Choose JDBC-backed sessions when the application depends on a relational database and SQL-oriented session operations are an acceptable trade-off against Redis latency.

## JDBC baseline

```xml
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-jdbc</artifactId>
</dependency>
```

```groovy
implementation 'org.springframework.session:spring-session-jdbc'
```

Application properties:

```yaml
spring:
  session:
    store-type: jdbc
    timeout: 45m
    jdbc:
      table-name: SPRING_SESSION
      initialize-schema: always
  datasource:
    url: jdbc:postgresql://localhost:5432/sessions
    driver-class-name: org.postgresql.Driver
    username: app_user
    password: secret
```

## Repository customization

Use `SessionRepositoryCustomizer` to adjust table name, timeout, and cleanup:

```java
@Bean
SessionRepositoryCustomizer<JdbcIndexedSessionRepository> jdbcRepositoryCustomizer() {
    return repository -> {
        repository.setTableName("SPRING_SESSION");
        repository.setDefaultMaxInactiveInterval(Duration.ofMinutes(45));
        repository.setCleanupCron("0 * * * * *");
    };
}
```

## Cleanup behavior

The JDBC repository runs a scheduled cleanup task that deletes expired session rows.
Control its cadence:

```yaml
spring:
  session:
    jdbc:
      cleanup-cron: "0 * * * * *"
```

Disable cleanup when an external job or database-level TTL handles expiration:

```yaml
spring:
  session:
    jdbc:
      cleanup-cron: "-"
```

## Transaction manager

By default, Spring Session JDBC uses the primary `PlatformTransactionManager` and `DataSource`.
Use `@SpringSessionTransactionManager` and `@SpringSessionDataSource` qualifiers when session transactions must use a dedicated pair separate from the application's primary database infrastructure:

```java
@Bean("springSessionDataSource")
DataSource sessionDataSource() {
    return DataSourceBuilder.create()
        .url("jdbc:postgresql://localhost:5432/sessions")
        .username("session_user")
        .password("secret")
        .build();
}

@Bean("springSessionTransactionManager")
PlatformTransactionManager sessionTransactionManager(@Qualifier("springSessionDataSource") DataSource ds) {
    return new DataSourceTransactionManager(ds);
}
```

### Custom transaction operations

Provide a dedicated `TransactionOperations` bean when session write operations need different propagation from the application's default:

```java
@Bean("springSessionTransactionOperations")
TransactionOperations sessionTransactionOperations(
        @Qualifier("springSessionTransactionManager") PlatformTransactionManager tm) {
    TransactionTemplate template = new TransactionTemplate(tm);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template;
}
```

## JSON attribute serialization

Use the `@EnableJdbcHttpSession` attribute `serializationConverter` or provide a `ConversionService` bean named `springSessionConversionService` when session attributes must be serialized as JSON in the `SPRING_SESSION_ATTRIBUTES` table:

```java
@Bean("springSessionConversionService")
ConversionService sessionConversionService() {
    DefaultConversionService service = new DefaultConversionService();
    service.addConverter(MySerializableType.class, String.class, source -> {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(source);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize session attribute", e);
        }
    });
    service.addConverter(String.class, MySerializableType.class, source -> {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(source, MySerializableType.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot deserialize session attribute", e);
        }
    });
    return service;
}
```

## Schema initialization

Set `spring.session.jdbc.initialize-schema` to `always` during development.
In production, apply the schema once through a migration tool and set it to `never` or `embedded` to avoid repeated DDL execution.

Schema location: `org/springframework/session/jdbc/schema-*.sql`.

## Example integration test shape

```java
@SpringBootTest
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
        MvcResult second = mockMvc.perform(post("/cart/items")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-2\"}"))
            .andExpect(status().isOk())
            .andReturn();
        assertThat(second.getResponse().getContentAsString()).contains("\"itemCount\":2");
    }
}
```

## Gotchas

- Do not rely on default cleanup cadence without confirming that expired rows disappear at an acceptable rate for the workload.
- Do not assume transaction behavior is correct when Spring Session shares the primary datasource with unrelated database work.
  - Use qualified beans when isolation matters.
- Do not use JDBC casually for high session churn when Redis latency and TTL semantics are a better fit.
- Do not skip `initialize-schema: never` in production deployments that manage schema through a migration tool.
