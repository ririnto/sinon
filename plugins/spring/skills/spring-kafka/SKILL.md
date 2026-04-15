---
name: spring-kafka
description: >-
  Use this skill when the user asks to "use KafkaTemplate", "build a Spring Kafka listener", "configure retry or dead-letter handling", "test Kafka listener behavior in Spring", or needs guidance on Spring Kafka patterns.
---

# Spring Kafka

## Overview

Use this skill to design Spring Kafka producers, consumers, listener containers, retry and dead-letter handling, and Kafka-specific integration tests. The common case is one explicit event contract, one producer, one listener, and one deliberate failure policy. Focus on message contract and delivery semantics before tuning listener settings.

## Use This Skill When

- You are building Spring Kafka producers or listeners.
- You need retry, dead-letter, or listener error-handling policy.
- You need to verify listener delivery behavior, retry, or dead-letter handling with embedded Kafka or other Spring Kafka test support.
- You need a default Spring Kafka producer/consumer shape you can paste into a project.
- Do not use this skill when the problem is generic Spring Integration flow design without Kafka focus.

## Common-Case Workflow

1. Start from the message contract and delivery semantics.
2. Keep producer and consumer responsibilities explicit.
3. Define terminal failure behavior before adding retry or dead-letter handling.
4. Prove async listener behavior and Kafka-specific failure handling with Spring Kafka testing support rather than assuming delivery works.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.kafka</groupId>
  <artifactId>spring-kafka</artifactId>
</dependency>
```

## First Runnable Commands or Code Shape

Start with one producer and one listener:

```java
@Service
class OrderPublisher {
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    OrderPublisher(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    void publish(OrderCreatedEvent event) {
        kafkaTemplate.send("orders.created", event.id(), event);
    }
}

@KafkaListener(topics = "orders.created", groupId = "billing")
void onOrderCreated(OrderCreatedEvent event) {
    // handle event
}
```

---

*Applies when:* you need the default Spring Kafka happy path before advanced retry or topology work.

## Ready-to-Adapt Templates

Producer:

```java
@Service
class OrderPublisher {
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    OrderPublisher(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    void publish(OrderCreatedEvent event) {
        kafkaTemplate.send("orders.created", event.id(), event);
    }
}
```

---

*Applies when:* the application emits one event type to one topic.

Listener:

```java
@KafkaListener(topics = "orders.created", groupId = "billing")
void onOrderCreated(OrderCreatedEvent event) {
    // handle event
}
```

---

*Applies when:* the application consumes one event contract with one clear group identity.

Retry and dead-letter policy:

```java
@Bean
DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
    return new DefaultErrorHandler(
            new DeadLetterPublishingRecoverer(kafkaTemplate),
            new FixedBackOff(1000L, 3)
    );
}
```

---

*Applies when:* terminal failure behavior is already defined and retries are intentional rather than hopeful.

Integration test:

```java
@SpringBootTest
@EmbeddedKafka(topics = "orders.created")
class OrderConsumerTest {
}
```

---

*Applies when:* listener behavior, retry, dead-letter handling, or delivery semantics are part of the contract.

## Validate the Result

Validate the common case with these checks:

- the message contract is explicit and stable before listener logic grows
- producer and consumer responsibilities are separated cleanly
- retry exists only together with a defined terminal failure policy
- listener behavior and Kafka-specific failure handling are tested with Spring Kafka support when async delivery matters

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| delivery semantics, listener topology, or failure-policy heuristics | `./references/kafka-patterns.md` |
| producer, listener, error-handler, or test configuration recipes | `./references/kafka-config-recipes.md` |

## Invariants

- MUST make message contract and delivery semantics explicit.
- SHOULD keep producer and consumer responsibilities separate.
- MUST define failure behavior before adding retry or dead-letter handling.
- SHOULD test async listener behavior and Kafka-specific failure handling with Spring Kafka testing support.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| defining consumers before the event contract is stable | listener behavior drifts with a moving payload | stabilize the contract first |
| adding retry without dead-letter or terminal failure policy | failures loop without a clear terminal path | define retry and terminal handling together |
| leaving async listener behavior untested | delivery assumptions remain unproven | use embedded or container-backed tests where delivery is part of the contract |
| treating Kafka listener tests as generic Spring slice questions | messaging semantics and retry behavior get under-specified | keep Spring test-scope choice separate from Kafka-specific integration verification |

## Scope Boundaries

- Activate this skill for:
  - Spring Kafka producer and consumer patterns
  - retry and dead-letter handling
  - Spring Kafka testing support, including embedded-Kafka listener verification
- Do not use this skill as the primary source for:
  - generic Spring Integration flows without Kafka focus
  - batch-oriented job design
  - transactional consistency across messaging and persistence boundaries
  - non-Spring Kafka client concerns divorced from Spring usage
