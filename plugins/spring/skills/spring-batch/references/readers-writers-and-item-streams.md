# Spring Batch readers, writers, and item streams

Open this reference when the blocker is choosing or implementing an `ItemReader`, `ItemWriter`, delegate chain, `ItemStream`, or restart-safe file, database, JSON, XML, or messaging pipeline.

## Reader and writer selection blocker

Pick the reader or writer family from the source and sink first, then tune restart state.

| Source or sink | Start here |
| --- | --- |
| Flat files | `FlatFileItemReader`, `FlatFileItemWriter` |
| XML | StAX-based reader or writer |
| JSON | Jackson-backed reader or writer |
| JDBC | cursor, paging, or batch JDBC components |
| JPA | paging reader or delegated repository strategy |
| Mongo, JMS, Kafka, AMQP | integration-backed or appendix-supported components |
| Multiple files | multi-resource readers or writers |

## Restart-safe state blocker

If restartability matters, keep cursor position, file offset, or aggregate state in an `ExecutionContext` through `ItemStream` semantics.

```java
class RestartableCustomerReader implements ItemStreamReader<CustomerInput> {
    @Override
    public void open(ExecutionContext executionContext) {
    }

    @Override
    public void update(ExecutionContext executionContext) {
    }
}
```

Use `ItemStream` whenever the component must reopen from saved state after failure.

## Delegate pattern blocker

Use delegates when the framework component does most of the work but a small customization is still needed.

```java
CompositeItemWriter<Customer> writer = new CompositeItemWriter<>();
```

Prefer composition over rewriting an existing reader or writer from scratch.

## Flat file baseline

```java
@Bean
FlatFileItemReader<CustomerInput> customerReader(Resource input) {
    return new FlatFileItemReaderBuilder<CustomerInput>().name("customerReader").resource(input).delimited().names("email", "name").targetType(CustomerInput.class).build();
}
```

## Database and paging blocker

Choose cursor versus paging based on transaction scope, memory profile, and restart behavior.

- cursor readers fit forward-only streaming workloads
- paging readers fit large tables with stable sort keys and restart requirements
- writers must align with transaction and idempotency expectations

## JSON and Jackson 3 blocker

Batch 6 aligns JSON support with newer Jackson baselines. Keep JSON reader and writer configuration on the same serialization stack as the rest of the application.

## Decision points

| Situation | First check |
| --- | --- |
| Restart duplicates or skips records | verify `ItemStream` state and key ordering |
| One reader almost fits but not quite | prefer a delegate wrapper over a rewrite |
| Large table processing is slow or unstable | choose cursor versus paging deliberately |
| File-based runs fail on restart | verify state persistence and resource naming |
