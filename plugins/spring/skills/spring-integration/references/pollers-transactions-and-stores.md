# Spring Integration pollers, transactions, and stores

Open this reference when the flow depends on pollers, transactional sources, aggregators, or persistent message stores.

## Poller shape

```java
Pollers.fixedDelay(Duration.ofSeconds(5))
    .maxMessagesPerPoll(10)
    .errorChannel("integration.errors")
```

## Transactional poller shape

```java
Pollers.fixedRate(Duration.ofSeconds(1))
    .transactional(transactionManager)
```

## Aggregator store shape

```java
.aggregate(aggregator -> aggregator
    .messageStore(messageStore)
    .expireGroupsUponCompletion(true))
```

## Fan-out and rejoin rules

- Add a splitter only when one message really must produce several correlated parts.
- Define correlation and release rules explicitly before adding an aggregator to the path.
- Add timeouts and persistent stores when rejoin state must survive slow or failed participants.

## Selection rules

- Use a poller only for sources that do not naturally push events.
- Add a transaction manager when the polled source and downstream side effects must commit or roll back together.
- Add persistent stores only when aggregation, resequencing, idempotency, or metadata must survive restarts.
