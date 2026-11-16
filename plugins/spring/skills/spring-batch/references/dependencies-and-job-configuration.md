# Dependencies and job configuration

## Dependency baseline

Before recommending a new version, check each selected Maven artifact on Maven Central for its latest stable release compatible with the project's Boot and Framework lines.
Keep project BOM or version-catalog management and existing pins unless the task authorizes changing them.

Use the Boot starter for application code and the Batch test module for job and step tests.

Spring Boot 4.0.x and 4.1.x manage Spring Batch 6.0.x.
The examples target the Spring Batch 6.0.x release line.
Spring Boot 3.4.x and 3.5.x use the Spring Batch 5.2.x compatibility branch.
Batch 6-specific APIs require a Boot 4.x path or an intentional compatible Spring Batch 6.x dependency.
The Boot 4.1-specific starter and auto-configuration examples below require that Boot release line.

JDBC store (default when a `DataSource` is present):

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-batch</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.batch</groupId>
        <artifactId>spring-batch-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

MongoDB store (Boot 4.1+):

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-batch-data-mongodb</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.batch</groupId>
        <artifactId>spring-batch-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Treat the Boot-managed path and the standalone current-line path as different compatibility branches.
Verify the actual Batch line before copying infrastructure or migration examples that depend on Batch 6 behavior.

## First safe configuration

### Infrastructure shape

On the Boot 3.4.x and 3.5.x Batch 5.2.x path, use Boot auto-configuration unless manual infrastructure control is required.
For manual control, `@EnableBatchProcessing` configures JDBC infrastructure using existing `DataSource` and `PlatformTransactionManager` beans.
It does not create the transaction manager.
The default bean names are `dataSource` and `transactionManager`:

```java
@Configuration
@EnableBatchProcessing
class BatchInfrastructureConfiguration {
}
```

On Spring Boot 4.1+, the auto-configured store is selected by classpath and conditions:

- `BatchJdbcAutoConfiguration` activates when a `DataSource` and `PlatformTransactionManager` are present (the default when `spring-boot-starter-batch` is on the classpath alongside any JDBC starter).
- `BatchDataMongoAutoConfiguration` activates when a `MongoDatabaseFactory` is present and no JDBC store is configured (the default when `spring-boot-starter-batch-data-mongodb` is on the classpath).
- `BatchAutoConfiguration` provides a `ResourcelessJobRepository` fallback when neither JDBC nor MongoDB conditions are met.

The resourceless repository does not persist metadata and does not support restartability or concurrent execution.
Use a persistent repository when jobs require these capabilities.

No `@EnableBatchProcessing` or `@EnableJdbcJobRepository`/`@EnableMongoJobRepository` annotations are needed on the Boot 4.1+ auto-configured path.
Boot backs off its auto-configuration when it detects `@EnableBatchProcessing` or a `DefaultBatchConfiguration` subclass on the classpath.

On Spring Batch 6 with manual control (for example, when you need custom `tablePrefix`, `transactionManagerRef`, or other annotation attributes that go beyond Boot properties), opt into a persistent backend explicitly:

```java
@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository
class BatchInfrastructureConfiguration {
}
```

```java
@Configuration
@EnableBatchProcessing
@EnableMongoJobRepository
class BatchInfrastructureConfiguration {
}
```

Boot 4.1+ MongoDB schema initialization property:

```yaml
spring:
  batch:
    data:
      mongodb:
        schema:
          initialize: true
```

Boot 4.1+ JDBC schema initialization property (unchanged from earlier lines):

```yaml
spring:
  batch:
    jdbc:
      initialize-schema: always
```

Change repository strategy only when operations, scale, or platform constraints require it.
Open [references/6-migration.md](6-migration.md) directly for Batch 6-specific migration behavior beyond these annotations.

### Chunk job baseline

```java
@Configuration
class ImportJobConfiguration {
    @Bean
    Job importJob(JobRepository repository, Step importStep) {
        return new JobBuilder("importJob", repository)
            .start(importStep)
            .build();
    }

    @Bean
    Step importStep(JobRepository repository, PlatformTransactionManager tx, ItemReader<Customer> reader, ItemWriter<Customer> writer) {
        return new StepBuilder("importStep", repository)
            .<Customer, Customer>chunk(100, tx)
            .reader(reader)
            .writer(writer)
            .build();
    }
}
```

## Core batch terms

Keep the batch runtime vocabulary explicit:

| Type | Role |
| --- | --- |
| `JobInstance` | one logical run identity defined by the identifying job parameters |
| `JobExecution` | one concrete execution attempt of a job instance |
| `JobParameters` | launch parameters that define identity or operational knobs |
| `JobLauncher` | application-driven job start surface |
| `ExecutionContext` | restart state persisted across step or job execution |
| `JobRepository` | metadata store for instances, executions, and step state |
| `JobOperator` | operational control surface for launch, stop, restart, and Batch 6-only recovery |
