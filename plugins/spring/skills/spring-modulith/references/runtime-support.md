# Runtime Support

Open this reference when the application needs startup verification, module initializer ordering, module-specific Flyway migrations, actuator endpoints, observability, or change-aware test execution.

## Runtime boundary

Use runtime support when module metadata must be available at runtime, module initializers must follow dependency order, or the module structure must be verified on startup.
Do not add runtime dependencies when only verification and documentation tests are needed.

## Runtime dependency

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-runtime</artifactId>
    <scope>runtime</scope>
</dependency>
```

This enables:

- `ApplicationModulesRuntime` bean to access `ApplicationModules` at runtime.
- `RuntimeApplicationModuleVerifier` for startup verification.
- `ApplicationModuleInitializer` invocation ordered by module dependency tree.

## Startup verification

```properties
spring.modulith.runtime.verification-enabled=true
```

When enabled, the application fails to start if module boundary violations are detected.

## Module initializer shape

```java
@Component
class CatalogDataLoader implements ApplicationModuleInitializer {

    @Override
    public void initialize() {
    }
}
```

Initializers run in module dependency order.
If module B depends on module A, A's initializers run before B's.
This requires `spring-modulith-runtime` on the classpath for topological sorting.

## Module-specific Flyway migrations

Enable module-aware Flyway:

```properties
spring.modulith.runtime.flyway-enabled=true
```

With this enabled, the Flyway setup is customized:

- Root migration folder becomes `db/migration/__root`.
  - Uses the default `flyway_schema_history` tracking table.
- Module-specific migrations live in `db/migration/$moduleIdentifier`.
  - Each uses `flyway_schema_history_$moduleIdentifier` as its tracking table.
- Module-specific migrations are set to baseline version 0 and baseline on migrate.
- Version numbers in migration scripts are scoped to the module.
  - Do not use global ordering.
- Migrations execute in module dependency tree order.
- Module tests only run the root migration and the migrations for modules included in the test run.
- Locations ending in a wildcard are not customized.
- Individual modules can skip Flyway migration by setting `spring.modulith.runtime.flyway.modules.$moduleIdentifier.skip-migration=true`.

## Actuator endpoint

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-actuator</artifactId>
    <scope>runtime</scope>
</dependency>
```

Exposes the application module structure via the Spring Boot actuator.

## Observability

The observability support is split into an API artifact and a core artifact:

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-observability-api</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-observability</artifactId>
    <scope>runtime</scope>
</dependency>
```

The API artifact provides observability interfaces used in application code.
The core artifact provides the runtime implementation for application module interaction observability.

## Insight starter

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-starter-insight</artifactId>
    <scope>runtime</scope>
</dependency>
```

Includes both actuator and observability support in one starter.

## Change-aware test execution

Skip tests in unaffected modules during development:

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-junit</artifactId>
    <scope>test</scope>
</dependency>
```

Tests run only for modules that have changed or transitively depend on changed modules.
The optimization backs off when:

- Running from an IDE (assumes explicit invocation).
- Build files or classpath resources have changed.
- No changes are detected (typical CI clean build).

For CI, set the reference commit:

```properties
spring.modulith.test.file-modification-detector=reference-commit
spring.modulith.test.reference-commit=<last-successful-build-commit>
```

For local development, use the default detector which considers uncommitted changes and unpushed commits.

## Custom module source factory

Contribute application modules from packages outside the main application class:

```java
public class CustomApplicationModuleSourceFactory implements ApplicationModuleSourceFactory {

    @Override
    public List<String> getRootPackages() {
        return List.of("com.acme.shared");
    }

    @Override
    public ApplicationModuleDetectionStrategy getApplicationModuleDetectionStrategy() {
        return ApplicationModuleDetectionStrategy.explicitlyAnnotated();
    }

    @Override
    public List<String> getModuleBasePackages() {
        return List.of("com.acme.module");
    }
}
```

Register in `META-INF/spring.factories`:

```properties
org.springframework.modulith.core.ApplicationModuleSourceFactory=example.CustomApplicationModuleSourceFactory
```

## Decision points

| Situation | Use |
| --- | --- |
| Verify boundaries at startup | `spring.modulith.runtime.verification-enabled=true` |
| Module startup code must respect dependency order | `ApplicationModuleInitializer` |
| Each module owns its database migrations | Module-specific Flyway |
| A module must skip Flyway migration | `spring.modulith.runtime.flyway.modules.$moduleIdentifier.skip-migration=true` |
| Module structure should be visible via actuator | `spring-modulith-actuator` |
| Observability interfaces in application code | `spring-modulith-observability-api` |
| Runtime observability for module interactions | `spring-modulith-observability` |
| Skip unaffected module tests during development | `spring-modulith-junit` |

## Verification rule

Verify startup verification catches a deliberate boundary violation before enabling it in production.
Verify Flyway module migrations execute in dependency order by confirming the schema state after a clean migration run.
