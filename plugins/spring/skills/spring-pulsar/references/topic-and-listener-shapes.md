# Topic and listener shapes

## Output and configuration shapes

## Topic shape

```text
shipments
persistent://public/default/shipments
```

## Topic pattern shape

```java
@PulsarListener(topicsPattern = "shipments-.*", subscriptionName = "warehouse")
```

## Subscription shape

```text
warehouse
```

## Listener declaration shape

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse")
```

## Acknowledgment mode shape

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse", ackMode = AckMode.RECORD)
```

## Dead-letter policy shape

```java
DeadLetterPolicy.builder()
    .maxRedeliverCount(5)
    .deadLetterTopic("shipments-dlt")
    .build();
```
