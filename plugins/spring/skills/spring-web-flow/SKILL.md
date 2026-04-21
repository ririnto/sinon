---
name: "spring-web-flow"
description: "Use this skill when building stateful multi-step web conversations with Spring Web Flow, including flow definitions, conversation-scoped state, transitions, validation, subflows, and flow execution tests."
metadata:
  title: "Spring Web Flow"
  official_project_url: "https://spring.io/projects/spring-webflow"
  reference_doc_urls:
    - "https://docs.spring.io/spring-webflow/docs/current/reference/"
  version: "4.0.0"
---

Use this skill when building stateful multi-step web conversations with Spring Web Flow, including flow definitions, conversation-scoped state, transitions, validation, subflows, and flow execution tests.

## Boundaries

Use `spring-web-flow` for guided browser conversations such as checkouts, onboarding wizards, and review-confirm-submit journeys that need explicit state transitions.

- Use ordinary stateless controller and page-rendering patterns when conversation state is not needed.
- Keep domain logic outside the flow definition. The flow should orchestrate steps and invoke application services, not contain business rules inline.
- Keep scope tuning, custom validation timing, global exception handling, and specialized testing variants out of the ordinary path unless they are the actual blocker.

## Common path

The ordinary Spring Web Flow job is:

1. Draw the user journey as named states and transitions before writing XML.
2. Keep mutable user input in flow-scoped variables and reserve conversation scope for truly cross-flow state.
3. Use view states for user input, decision states for branching, action states for side effects, and end states for completion.
4. Invoke a service only at explicit transition points such as validate, price, reserve, or submit.
5. Add a flow execution test that proves startup, one forward transition, and the end-state outcome before adding deeper scope or recovery behavior.

### Branch selector

- Stay in `SKILL.md` for the ordinary wizard path: flow definition structure, flow registry and executor setup, view-state model binding, simple validation, decision states, subflows, and basic flow execution tests.
- Open [references/flow-scopes.md](references/flow-scopes.md) when scope lifetimes, scope searching, or cross-flow shared state become the blocker.
- Open [references/validation-and-exception-handling.md](references/validation-and-exception-handling.md) when validation timing, grouped validation, or global recovery paths need explicit tuning.
- Open [references/flow-execution-testing.md](references/flow-execution-testing.md) when the task needs deeper execution-test patterns such as backtracking, subflow exits, or exception-path verification.

## Dependency baseline

Use the core Web Flow module for ordinary MVC-integrated flows.

Spring Web Flow 4.0.0 is the current released line and targets Java 17+, Spring Framework 7.0, and Servlet 6.1.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.webflow</groupId>
        <artifactId>spring-webflow</artifactId>
        <version>4.0.0</version>
    </dependency>
</dependencies>
```

## First safe configuration

### Flow registry shape

```java
@Bean
FlowDefinitionRegistry flowRegistry() {
    return getFlowDefinitionRegistryBuilder()
        .setBasePath("/WEB-INF/flows")
        .addFlowLocationPattern("/**/*-flow.xml")
        .build();
}
```

### Flow executor shape

```java
@Bean
FlowExecutor flowExecutor() {
    return getFlowExecutorBuilder(flowRegistry()).build();
}
```

### MVC integration shape

```java
@Bean
FlowHandlerMapping flowHandlerMapping() {
    FlowHandlerMapping mapping = new FlowHandlerMapping();
    mapping.setOrder(-1);
    mapping.setFlowRegistry(flowRegistry());
    return mapping;
}

@Bean
FlowHandlerAdapter flowHandlerAdapter() {
    FlowHandlerAdapter adapter = new FlowHandlerAdapter();
    adapter.setFlowExecutor(flowExecutor());
    return adapter;
}
```

Keep the flow id aligned with the URL mapping strategy. A flow registered as `booking-flow` should be reachable through the MVC flow handler path you standardize on.

Start with one registry path and one executor. Add custom execution listeners or repositories only when the application really needs them.

## Coding procedure

1. Name states after user-visible steps or domain actions, not technical implementation details.
2. Keep transitions explicit and small so the next state is obvious from each event.
3. Use `model` binding and validation in view states instead of duplicating form parsing in actions.
4. Store only the state needed to continue the conversation; large aggregates belong in services or persistence.
5. Use subflows only when a nested journey is reusable or independently testable.
6. Test both the happy path and at least one backward or invalid-input path.

## Edge cases

- Open [references/flow-scopes.md](references/flow-scopes.md) when `viewScope`, `conversationScope`, or scope lifetime rules become the design constraint.
- Open [references/validation-and-exception-handling.md](references/validation-and-exception-handling.md) when validation timing or shared recovery paths need more than the ordinary path.
- Open [references/flow-execution-testing.md](references/flow-execution-testing.md) when the flow behavior itself needs deeper execution-test coverage than a simple happy path.

## Implementation examples

### Flow definition

```xml
<flow xmlns="http://www.springframework.org/schema/webflow"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://www.springframework.org/schema/webflow https://www.springframework.org/schema/webflow/spring-webflow.xsd">

    <var name="booking" class="com.example.Booking"/>

    <view-state id="enterDetails" model="booking">
        <transition on="next" to="confirm" validate="true"/>
    </view-state>

    <view-state id="confirm" model="booking">
        <transition on="confirm" to="saveBooking"/>
        <transition on="back" to="enterDetails"/>
    </view-state>

    <action-state id="saveBooking">
        <evaluate expression="bookingService.save(booking)"/>
        <transition on="success" to="finished"/>
    </action-state>

    <end-state id="finished" view="booking/complete"/>
</flow>
```

### MVC wiring

```java
@Configuration
class WebFlowConfig extends AbstractFlowConfiguration {
    @Bean
    FlowDefinitionRegistry flowRegistry() {
        return getFlowDefinitionRegistryBuilder()
            .setBasePath("/WEB-INF/flows")
            .addFlowLocationPattern("/**/*-flow.xml")
            .build();
    }

    @Bean
    FlowExecutor flowExecutor() {
        return getFlowExecutorBuilder(flowRegistry()).build();
    }

    @Bean
    FlowHandlerMapping flowHandlerMapping() {
        FlowHandlerMapping mapping = new FlowHandlerMapping();
        mapping.setOrder(-1);
        mapping.setFlowRegistry(flowRegistry());
        return mapping;
    }

    @Bean
    FlowHandlerAdapter flowHandlerAdapter() {
        FlowHandlerAdapter adapter = new FlowHandlerAdapter();
        adapter.setFlowExecutor(flowExecutor());
        return adapter;
    }
}
```

### Decision state shape

```xml
<decision-state id="checkEligibility">
    <if test="booking.vipCustomer" then="vipReview" else="standardReview"/>
</decision-state>
```

### Subflow shape

```xml
<subflow-state id="collectPayment" subflow="payment-flow">
    <input name="bookingId" value="booking.id"/>
    <transition on="paid" to="finished"/>
</subflow-state>
```

## Output and configuration shapes

Return these implementation artifacts for the ordinary path:

1. One `*-flow.xml` definition with explicit states and transitions
2. One `WebFlowConfig` or equivalent configuration class that registers the flow and executor
3. One `*FlowExecutionTests` class that proves startup, one forward transition, and one negative or backtracking path

### State naming shape

```text
enterDetails
confirm
saveBooking
finished
```

### Transition shape

```xml
<transition on="next" to="confirm"/>
```

### Flow variable shape

```xml
<var name="booking" class="com.example.Booking"/>
```

## Testing checklist

- Verify the expected event moves the flow to the correct next state.
- Verify invalid input stays on the same view state and exposes validation errors.
- Verify backward navigation preserves only the state that should survive.
- Verify subflow entry and exit events map back to the parent flow correctly.
- Verify the flow reaches the intended end state on the happy path.

## Production checklist

- Keep state ids and transition events stable when bookmarks, tests, or UI logic depend on them.
- Avoid storing large mutable graphs in conversation scope.
- Keep side-effecting actions idempotent or guarded against duplicate submission.
- Ensure session timeout behavior is acceptable for long-running user journeys.
- Treat flow execution tests as part of the user-journey compatibility surface.

## References

- Open [references/flow-scopes.md](references/flow-scopes.md) when scope tradeoffs or scope lifetime behavior become the blocker.
- Open [references/validation-and-exception-handling.md](references/validation-and-exception-handling.md) when custom validation timing or shared recovery paths need deeper guidance.
- Open [references/flow-execution-testing.md](references/flow-execution-testing.md) when the ordinary wizard flow is not enough and the task needs deeper flow execution testing patterns.
