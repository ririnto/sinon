# Spring Data multimodule repository scanning

Open this reference when the blocker is strict repository scanning across more than one Spring Data store module.

## Strict scanning blocker

When more than one Spring Data store module is present, do not let repository discovery guess which store owns a repository.

```java
@Configuration
@EnableJpaRepositories(basePackages = "com.example.jpa")
@EnableMongoRepositories(basePackages = "com.example.mongo")
class DataConfig {
}
```

Keep base packages explicit so repository ownership stays obvious and cross-store ambiguity does not depend on classpath order.

## Multi-store configuration shape

```java
@Configuration
class JpaStoreConfiguration {
    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.example.jpa");
        return factory;
    }
}

@Configuration
class MongoStoreConfiguration {
    @Bean
    MongoTemplate mongoTemplate(MongoDatabaseFactory factory, MappingMongoConverter converter) {
        return new MongoTemplate(factory, converter);
    }
}
```

## Domain-annotation blocker

Module-specific domain annotations such as `@Entity` or `@Document` help repository ownership stay unambiguous, but base-package scoping is still the safer boundary when multiple stores coexist.

## Decision points

| Situation | First check |
| --- | --- |
| One application uses JPA and MongoDB together | scope repositories with explicit base packages |
| Repository binding looks ambiguous | verify store-specific annotations and repository hierarchy |
| Shared package scanning crosses store boundaries | split scanning configuration before adding more repositories |

## Verification rule

Verify startup binds each repository to the intended store and does not log ambiguous repository-assignment warnings.
