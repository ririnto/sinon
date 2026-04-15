---
name: spring-integration
description: >-
  Use this skill when the user asks to "build a Spring integration flow", "route messages", "use channels and adapters", "model enterprise integration patterns in Spring", or needs guidance on Spring Integration flow design.
---

# Spring Integration

## Overview

Use this skill to design Spring Integration flows around messages, channels, adapters, routers, and handlers. The common case is one inbound edge, one explicit transform or route, and one outbound edge, with transport details kept at the boundary. Focus on the message flow shape before choosing specific integration adapters.

## Use This Skill When

- You are designing Spring Integration flows with channels, routers, transformers, or aggregators.
- You need a default IntegrationFlow shape you can paste into a codebase.
- You need to decide where adapter boundaries and message-routing rules belong.
- Do not use this skill when the main problem is Kafka-specific consumer/producer design or batch-job structure.

## Common-Case Workflow

1. Identify the message source, the transformation steps, and the destination.
2. Pick the simplest channel and endpoint types that fit the flow.
3. Keep transport-specific adapters at the edge of the flow.
4. Model routing, transformation, and aggregation explicitly rather than hiding them inside unrelated services.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-integration</artifactId>
</dependency>
```

## First Runnable Commands or Code Shape

Start with one inbound adapter, one transform, and one output channel:

```java
@Bean
IntegrationFlow fileImportFlow() {
    return IntegrationFlow
            .from(Files.inboundAdapter(Path.of("inbox").toFile()), c -> c.poller(Pollers.fixedDelay(1000)))
            .transform(String.class, payload -> payload.trim())
            .channel("outboundChannel")
            .get();
}
```

---

*Applies when:* you need the default Spring Integration flow shape before more advanced routing or aggregation.

## Ready-to-Adapt Templates

Transform flow:

```java
@Bean
IntegrationFlow orderFlow() {
    return IntegrationFlow.from("inputChannel")
            .transform(String.class, payload -> payload.trim())
            .channel("outputChannel")
            .get();
}
```

---

*Applies when:* one message shape must be cleaned up or transformed before handoff.

Router flow:

```java
@Bean
IntegrationFlow routerFlow() {
    return IntegrationFlow.from("inputChannel")
            .<String, Boolean>route(payload -> payload.startsWith("A"), m -> m
                    .channelMapping(true, "alphaChannel")
                    .channelMapping(false, "otherChannel"))
            .get();
}
```

---

*Applies when:* several destinations are possible and the routing rule belongs in the flow.

Service activator endpoint:

```java
@Bean
IntegrationFlow handlerFlow(OrderHandler handler) {
    return IntegrationFlow.from("inputChannel")
            .handle(handler, "handle")
            .get();
}
```

---

*Applies when:* the flow reaches an application service boundary after transformation or routing.

## Validate the Result

Validate the common case with these checks:

- adapters stay at the edge of the flow
- routing and transformation remain explicit in the flow definition
- channel choices match the needed coupling or decoupling semantics
- transport-specific logic is not hidden in business handlers

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| channel choice, router/filter tradeoffs, or flow-shape heuristics | `./references/integration-patterns.md` |

## Invariants

- MUST keep adapters at the edge.
- SHOULD keep message transformations explicit.
- MUST model routing and aggregation deliberately.
- SHOULD choose the smallest flow that satisfies the integration need.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| embedding transport-specific logic in the middle of a business flow | the message flow stops being portable and readable | keep adapters and transport concerns at the edge |
| using too many intermediate channels for a simple integration | the flow becomes harder to follow than the business need | keep the channel topology minimal |
| hiding routing decisions inside unrelated service classes | the actual integration graph becomes invisible | express routing in the flow itself |

## Scope Boundaries

- Activate this skill for:
  - Spring Integration flow design
  - channels, routers, filters, transformers, and aggregators
  - message-driven adapter composition
- Do not use this skill as the primary source for:
  - Kafka-specific producer/consumer design
  - batch job design
  - transactional consistency across messaging and persistence
