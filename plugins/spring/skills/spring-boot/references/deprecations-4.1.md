# Spring Boot 4.1.0 removals and deprecations

Open this reference when migrating from 4.0 to 4.1 or replacing removed/deprecated features.

## Removed

### Layertools jar mode

The `layertools` jar mode was removed. Use `tools` jar mode which provides the same functionality.

```sh
java -Djarmode=tools -jar app.jar extract --layers --destination extracted
java -Djarmode=tools -jar app.jar list-layers
```

### Deprecated Logback properties

The following `logging.file.*` properties were removed. Migrate to `logging.logback.rollingpolicy.*`.

| Removed property | Replacement |
| --- | --- |
| `logging.file.clean-history-on-start` | `logging.logback.rollingpolicy.clean-history-on-start` |
| `logging.file.max-history` | `logging.logback.rollingpolicy.max-history` |
| `logging.file.max-size` | `logging.logback.rollingpolicy.max-file-size` |
| `logging.file.total-size-cap` | `logging.logback.rollingpolicy.total-size-cap` |
| `logging.pattern.rolling-file-name` | `logging.logback.rollingpolicy.file-name-pattern` |

### `-DskipTests` no longer skips AOT

`-DskipTests` now only skips test execution, not AOT processing. To skip both tests and AOT, use `-Dmaven.test.skip`.

```sh
./mvnw spring-boot:process-aot -Dmaven.test.skip
```

## Deprecated

### Derby support

Apache Derby has been retired. The following are deprecated for removal:
- `org.springframework.boot.jdbc.DatabaseDriver.DERBY`
- `org.springframework.boot.jdbc.EmbeddedDatabaseConnection.DERBY`

Migrate to H2 or HSQLDB.

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

### LiveReload in DevTools

`spring.devtools.livereload.enabled` and `spring.devtools.livereload.port` are deprecated with no replacement. Remove these properties.

### Dynatrace V1 API

Dynatrace V1 API properties and classes are deprecated. Migrate to the V2 API.

| Deprecated property | Action |
| --- | --- |
| `management.dynatrace.metrics.export.v1.device-id` | Remove, use V2 |
| `management.dynatrace.metrics.export.v1.group` | Remove, use V2 |
| `management.dynatrace.metrics.export.v1.technology-type` | Remove, use V2 |

### RootUriTemplateHandler

`RootUriTemplateHandler` is deprecated in favor of `DefaultUriBuilderFactory`.

## Validation rule

Run the application with `-Ddebug` and check for deprecated property warnings before upgrading.
