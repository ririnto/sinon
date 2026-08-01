# Spring Boot 4.1 changes

Open this reference when migrating from 4.0 to 4.1 or replacing features that changed.

## Removed

### Layertools jar mode

The `layertools` jar mode was removed.
Use `tools` jar mode which provides the same functionality.

```sh
java -Djarmode=tools -jar app.jar extract --layers --destination extracted
java -Djarmode=tools -jar app.jar list-layers
```

### Changed Logback properties

The following `logging.file.*` properties were removed.
Migrate to `logging.logback.rollingpolicy.*`.

| Removed property | Replacement |
| --- | --- |
| `logging.file.clean-history-on-start` | `logging.logback.rollingpolicy.clean-history-on-start` |
| `logging.file.max-history` | `logging.logback.rollingpolicy.max-history` |
| `logging.file.max-size` | `logging.logback.rollingpolicy.max-file-size` |
| `logging.file.total-size-cap` | `logging.logback.rollingpolicy.total-size-cap` |
| `logging.pattern.rolling-file-name` | `logging.logback.rollingpolicy.file-name-pattern` |

### `-DskipTests` no longer skips AOT

`-DskipTests` now only skips test execution, not AOT processing.
To skip both tests and AOT, use `-Dmaven.test.skip`.

```sh
./mvnw spring-boot:process-aot -Dmaven.test.skip
```

## Changed

### Derby support

Derby support is deprecated in 4.1 and slated for removal.
`org.springframework.boot.jdbc.DatabaseDriver.DERBY` and `org.springframework.boot.jdbc.EmbeddedDatabaseConnection.DERBY` are deprecated.
Migrate to H2 or HSQLDB.

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

### LiveReload in DevTools

`spring.devtools.livereload.enabled` and `spring.devtools.livereload.port` are deprecated in 4.1 with no replacement.
They are still functional, but Live Reload has been disabled by default since 4.0; set `spring.devtools.livereload.enabled=true` to re-enable it.

### Dynatrace V1 API

Dynatrace V1 API properties are deprecated in 4.1; use the V2 API instead.

| Deprecated property | Action |
| --- | --- |
| `management.dynatrace.metrics.export.v1.device-id` | Deprecated, use V2 |
| `management.dynatrace.metrics.export.v1.group` | Deprecated, use V2 |
| `management.dynatrace.metrics.export.v1.technology-type` | Deprecated, use V2 |

## Validation rule

Run the application with `-Ddebug` and check for property warnings before upgrading.
