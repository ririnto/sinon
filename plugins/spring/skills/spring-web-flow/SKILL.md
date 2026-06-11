---
name: spring-web-flow
description: >-
  Build Spring Web Flow browser conversations with flow definitions, conversation state, scoped variables, validation, exception handling, MVC integration, and flow execution tests. Use when implementing guided checkouts, onboarding wizards, review-confirm-submit flows, or other stateful web conversations.
---

# Spring Web Flow

Use `spring-web-flow` for guided browser conversations that need explicit states, transitions, scoped data, validation timing, and flow execution tests. Use `spring-framework` for ordinary Spring MVC, WebFlux, WebClient, container, transaction, event, and TestContext work.

The current public Spring Web Flow artifact line is 4.0.1.

## Common path

1. Model the conversation states and transitions before writing controllers or flow XML.
2. Keep flow-scoped state explicit and avoid storing durable domain state in the web conversation.
3. Register flow definitions through a flow registry and executor.
4. Add validation and exception handling only at the transition points that need them.
5. Test representative flow executions, including the unhappy path that must return to a stable state.

## Surface map

| Surface | Open when |
| --- | --- |
| Flow scopes | [references/web-flow-scopes.md](references/web-flow-scopes.md) |
| Validation and exception handling | [references/web-flow-validation-and-exception-handling.md](references/web-flow-validation-and-exception-handling.md) |
| Flow execution tests | [references/web-flow-execution-testing.md](references/web-flow-execution-testing.md) |

## Dependency baseline

```xml
<dependency>
    <groupId>org.springframework.webflow</groupId>
    <artifactId>spring-webflow</artifactId>
    <version>4.0.1</version>
</dependency>
```

## Output contract

Return:

1. The flow definition or integration shape
2. The scoped state and transition contract
3. Validation, exception, or test changes
4. Remaining conversation-state risks
