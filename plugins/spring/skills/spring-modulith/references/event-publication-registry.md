# Event Publication Registry

Open this reference when module events must be tracked, replayed, or completed reliably after failures.

## Registry boundary

Use the event publication registry when application events must survive listener failures, system crashes, or broker unavailability. Do not use it when synchronous in-process event delivery without failure tracking is sufficient.

## Starter choices

| Persistence | Starter |
| --- | --- |
| JPA | `spring-modulith-starter-jpa` |
| JDBC | `spring-modulith-starter-jdbc` |
| MongoDB | `spring-modulith-starter-mongodb` |
| Neo4j | `spring-modulith-starter-neo4j` |

The JDBC starter works in JPA-based applications but bypasses the JPA provider for event persistence.

## Schema initialization

Automatic schema creation for the JDBC event publication table is enabled by default. Disable when managing the schema externally:

```properties
spring.modulith.events.jdbc.schema-initialization.enabled=false
```

## Publication lifecycle (2.0)

Each publication transitions through states tracked by `EventPublication.Status`:

- `PUBLISHED` -- stored, waiting to be processed.
- `PROCESSING` -- a listener has claimed the publication and is executing.
- `COMPLETED` -- the listener finished successfully.
- `FAILED` -- the listener threw an exception, or the publication was marked failed by the staleness monitor.
- `RESUBMITTED` -- a previously failed publication was resubmitted and is pending processing.

Each publication also tracks `completionAttempts` (how many times the listener was invoked) and `lastResubmissionDate`.

## Completion modes

```properties
spring.modulith.events.completion-mode=update
```

| Mode | Behavior |
| --- | --- |
| `update` (default) | Sets the completion date on the publication entry. Requires periodic purging via `CompletedEventPublications`. |
| `delete` | Removes the publication entry on completion. No completed publications are accessible. |
| `archive` | Copies the entry to an archive table and removes the original. Completed publications remain accessible. |

## Housekeeping shape

```java
@Autowired
CompletedEventPublications completed;

completed.purgeOlderThan(Duration.ofDays(1));
```

## Incomplete publications shape

```java
@Autowired
IncompleteEventPublications incomplete;

incomplete.resubmitIncompletePublications(
    Duration.ofMinutes(5));
```

## Failed publications shape (2.0)

```java
@Autowired
FailedEventPublications failed;

failed.resubmit(ResubmissionOptions.defaults()
    .withBatchSize(50)
    .withMinAge(Duration.ofMinutes(1))
    .withMaxInFlight(10));
```

`ResubmissionOptions` controls batch size, maximum in-flight publications, minimum age before resubmission, and an optional filter by event type or completion attempts. Use `ResubmissionOptions.defaults()` to start and chain builder methods.

## Staleness monitor (2.0)

Configure the background task that marks stale publications as `FAILED`:

```properties
spring.modulith.events.staleness.check-interval=PT1M
spring.modulith.events.staleness.published=PT5M
spring.modulith.events.staleness.processing=PT10M
spring.modulith.events.staleness.resubmitted=PT5M
```

When any staleness duration is non-zero, the monitor runs at the configured interval and marks publications in the corresponding states as `FAILED`. When all are zero (default), no automatic marking occurs.

## Custom event serializer

Register a custom `EventSerializer` bean when the default Jackson serializer does not fit:

```java
@Bean
EventSerializer customSerializer(ObjectMapper mapper) {
    return new CustomEventSerializer(mapper);
}
```

## Custom publication date

Register a `Clock` bean to override the default `Clock.systemUTC()`:

```java
@Bean
Clock fixedClock() {
    return Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
}
```

## Republish on restart

```properties
spring.modulith.events.republish-outstanding-events-on-restart=true
```

Not recommended in multi-instance deployments because other instances may still be processing events.

## EPR opt-out for @TransactionalEventListener

By default, the event publication registry tracks events published via `@TransactionalEventListener`. Opt out when a listener should bypass the registry:

```java
@Component
class NonTrackedListener {
    @TransactionalEventListener
    @ApplicationModuleListener(publishEvent = false)
    void on(OrderCompleted event) {
    }
}
```

Use `publishEvent = false` on `@ApplicationModuleListener` to prevent the registry from recording the event publication.

## Neo4j identifier hashing

The Neo4j event publication registry uses SHA-256 for event identifier hashing in 2.1.0. Existing Neo4j EPR deployments on earlier versions store identifiers with a different hash and must reprocess or migrate outstanding publications after upgrading.

## JdbcEventPublicationRepository deletion fix

A bug in versions before 2.1.0 caused `JdbcEventPublicationRepository.delete(…)` to fail under certain conditions. This is fixed in 2.1.0.

## Decision points

| Situation | Use |
| --- | --- |
| Events must survive listener failures | Use the registry with `REQUIRES_NEW` listeners |
| Completed entries must not grow unbounded | Set `completion-mode=delete` or schedule purges |
| Failed events must be retried selectively | Use `FailedEventPublications.resubmit(ResubmissionOptions)` |
| Crashes leave publications stuck | Configure staleness monitor durations |
| In-memory-only event delivery is sufficient | Do not add a registry starter |

## Verification rule

Verify one test proves that a failed listener leaves the publication in a non-completed state and that `FailedEventPublications.resubmit(...)` moves it to `RESUBMITTED` before the listener processes it.
