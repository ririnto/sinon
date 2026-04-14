---
title: Spring Metrics and Observations Reference
description: >-
  Reference for Micrometer naming, ObservationRegistry usage, HTTP metrics, and low-cardinality tag design.
---

Use this reference when the main question is how to name metrics, add observations, and keep Spring operator data useful under production load.

## Naming Rule

Prefer stable lowercase dot-separated names.

Examples: `http.server.requests`, `http.client.requests`, `invoice.issue`, `scheduler.task.duration`

## Low-Cardinality Rule

Metric tags should stay bounded. Move high-cardinality values into tracing instead.

Good tags: `method`, `status`, `outcome`, `channel`

Bad tags: `userId`, `email`, `orderId`

## Timer Examples

Basic timer for a recurring operation:

```java
Timer.builder("scheduler.task.duration")
        .publishPercentileHistogram()
        .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofSeconds(1))
        .register(registry);
```

Counter for one-shot events:

```java
Counter.builder("payment.refundIssued")
        .tag("channel", "portal")
        .register(registry);
```

Gauge for current state:

```java
Gauge.builder("queue.depth", queue, Queue::size)
        .register(registry);
```

## `@Observed` Examples

Method-level observation — when one method expresses the unit of work cleanly:

```java
@Observed(name = "invoice.issue",
         lowCardinalityKeyValues = {"channel", "portal"})
Invoice issue(InvoiceCommand command) {
    return doIssue(command);
}
```

Programmatic observation with custom key values:

```java
Observation.createNotStarted("invoice.issue", observationRegistry)
        .lowCardinalityKeyValue("channel", command.channel())
        .observe(() -> doIssue(command));
```

## HTTP Instrumentation Examples

Auto-instrumented server requests produce `http.server.requests` with tags `uri`, `method`, `status`, `outcome`. Server-side instrumentation is enabled automatically when Boot auto-configuration is active.

Client requests instrumented via Boot-managed `RestClient` or `WebClient` builder produce `http.client.requests` with tags `uri`, `method`, `status`, `outcome`.

```java
@Bean
RestClient ordersRestClient(RestClient.Builder builder) {
    return builder
            .baseUrl("https://orders.internal")
            .build();
}
```

Direct instrumentation for unusual clients:

```java
MeterRegistry registry = ...;
HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
observationRegistry.adapt(context -> {
    context.put("http.url", url);
    context.put("http.method", "GET");
}).observe(() -> {
    connection.connect();
    // ...
});
```

## Common Mistakes

- inventing a new metric name for behavior Spring already instruments automatically
- using dynamic URI or entity ID values as metric tags
- wrapping every code path in manual timing code instead of using observations where a shared standard exists
