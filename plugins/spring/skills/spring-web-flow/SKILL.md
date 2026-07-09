---
name: spring-web-flow
description: >-
  Build Spring Web Flow browser conversations with flow definitions, conversation state, scoped variables, validation, exception handling, MVC integration, and flow execution tests.
  Use when implementing guided checkouts, onboarding wizards, review-confirm-submit flows, or other stateful web conversations.
---

# Spring Web Flow

Use `spring-web-flow` for guided browser conversations that need explicit states, transitions, scoped data, validation timing, and flow execution tests.
Use `spring-framework` for ordinary Spring MVC, WebFlux, WebClient, container, transaction, event, and TestContext work.

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

## Author a flow

A flow is an XML document of states and transitions.
The smallest useful flow is one view-state that transitions to an end-state.

Place it at `src/main/webapp/WEB-INF/flows/greeting/greeting-flow.xml`:

```xml
<flow xmlns="http://www.springframework.org/schema/webflow"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://www.springframework.org/schema/webflow
                          https://www.springframework.org/schema/webflow/spring-webflow.xsd">

    <view-state id="enterGreeting">
        <transition on="submit" to="done"/>
    </view-state>

    <end-state id="done"/>
</flow>
```

The flow id defaults to the file name minus its extension, so this example registers as `greeting-flow`.
Add `model`, `view`, `on-start`, `on-entry set`, `subflow-state`, and `global-transitions` only as the conversation needs them.

## Flow registry and executor

Register every flow XML in a `FlowDefinitionRegistry` and expose a `FlowExecutor` that launches them.
Extend `AbstractFlowConfiguration` and declare the two beans:

```java
@Configuration
public class WebFlowConfig extends AbstractFlowConfiguration {
    @Bean
    public FlowDefinitionRegistry flowRegistry() {
        return getFlowDefinitionRegistryBuilder()
                .addFlowLocation("/WEB-INF/flows/greeting/greeting-flow.xml")
                .build();
    }

    @Bean
    public FlowExecutor flowExecutor() {
        return getFlowExecutorBuilder(flowRegistry()).build();
    }

    @Bean
    public FlowHandlerMapping flowHandlerMapping(FlowDefinitionRegistry flowRegistry) {
        FlowHandlerMapping mapping = new FlowHandlerMapping();
        mapping.setFlowRegistry(flowRegistry);
        return mapping;
    }

    @Bean
    public FlowHandlerAdapter flowHandlerAdapter(FlowExecutor flowExecutor) {
        FlowHandlerAdapter adapter = new FlowHandlerAdapter();
        adapter.setFlowExecutor(flowExecutor);
        return adapter;
    }
}
```

To serve flows over HTTP, add `FlowHandlerMapping` and `FlowHandlerAdapter` to the same Spring MVC application context as the registry and executor.
The mapping routes requests whose path matches a flow id into the registry, and the adapter bridges each request to `flowExecutor`.
Both classes live in the `spring-webflow` artifact under `org.springframework.webflow.mvc.servlet`, so no extra dependency is needed.

## Output contract

Return:

1. The flow definition or integration shape
2. The scoped state and transition contract
3. Validation, exception, or test changes
4. Remaining conversation-state risks
