---
name: spring-web-flow
description: >-
  Build Spring Web Flow browser conversations with flow definitions, conversation state, scoped variables, validation, exception handling, MVC integration, and flow execution tests.
  Use when implementing guided checkouts, onboarding wizards, review-confirm-submit flows, or other stateful web conversations.
---

# Spring Web Flow

## Boundaries

Use this skill for guided browser conversations that need explicit states, transitions, flow-scoped models, backtracking, validation timing, recovery states, and flow execution tests.
Ordinary stateless MVC controllers, reactive HTTP handlers, outbound HTTP clients, container wiring, and transaction design are separate web or framework concerns.

The current public Spring Web Flow line is 4.0.x.
The dependency baseline below pins the current 4.0.1 artifact.

## Common path

1. Draw the happy-path states and event names before writing XML.
2. Put the multi-step form model in `flowScope` and keep durable domain state outside the conversation.
3. Define forward, backward, completion, validation, and technical-recovery transitions explicitly.
4. Register the flow before ordinary controller mappings and expose a `FlowExecutor` plus MVC adapter.
5. Render forms that post to `flowExecutionUrl` with a concrete `_eventId`.
6. Add one flow-execution test that starts the flow, follows the happy path, and verifies its outcome.

## Dependency baseline

```xml
<dependency>
    <groupId>org.springframework.webflow</groupId>
    <artifactId>spring-webflow</artifactId>
    <version>4.0.1</version>
</dependency>
```

## First safe commands

```sh
./mvnw test -Dtest=RegistrationFlowTests
```

```sh
./gradlew test --tests RegistrationFlowTests
```

## Two-step flow

Place the flow at `src/main/webapp/WEB-INF/flows/registration/registration-flow.xml`.
The `<var>` declaration creates `registration` in flow scope, so the same model survives both rendered steps.

```xml
<flow xmlns="http://www.springframework.org/schema/webflow"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://www.springframework.org/schema/webflow
                          https://www.springframework.org/schema/webflow/spring-webflow.xsd">

    <var name="registration" class="com.example.Registration"/>

    <view-state id="details" view="registration/details" model="registration">
        <transition on="next" to="review" validate="true"/>
    </view-state>

    <view-state id="review" view="registration/review" model="registration">
        <transition on="back" to="details" validate="false"/>
        <transition on="confirm" to="finished"/>
    </view-state>

    <end-state id="finished" view="registration/complete"/>

    <view-state id="technicalError" view="registration/technical-error">
        <transition on="retry" to="review" validate="false"/>
    </view-state>

    <global-transitions>
        <transition on-exception="java.lang.Exception" to="technicalError"/>
    </global-transitions>
</flow>
```

The flow id defaults to the filename without `.xml`, so this definition is addressed as `registration-flow`.
Validation runs on `next`, backward navigation skips validation, and unexpected failures converge on one renderable recovery state.
Keep expected business-rule failures in the model or a named transition rather than routing them through `technicalError`.

## Event forms and flow URL

Start the flow with a browser request to `/registration-flow`.
Every view rendered inside the conversation must post to the current `flowExecutionUrl`, which carries the flow execution key.

Details view:

```html
<form action="${flowExecutionUrl}" method="post">
    <label>
        Email
        <input name="email" value="${registration.email}">
    </label>
    <button type="submit" name="_eventId" value="next">Review</button>
</form>
```

Review view:

```html
<form action="${flowExecutionUrl}" method="post">
    <button type="submit" name="_eventId" value="back">Back</button>
    <button type="submit" name="_eventId" value="confirm">Confirm</button>
</form>
```

Do not post a flow form to the bare start URL after execution begins.
Doing so starts a new conversation instead of resuming the existing one.

## Registry, executor, and MVC mapping

Register the flow, expose its executor, and give `FlowHandlerMapping` an order before ordinary annotated-controller mappings.

```java
@Configuration
class WebFlowConfig extends AbstractFlowConfiguration {
    @Bean
    FlowDefinitionRegistry flowRegistry() {
        return getFlowDefinitionRegistryBuilder().addFlowLocation("/WEB-INF/flows/registration/registration-flow.xml").build();
    }

    @Bean
    FlowExecutor flowExecutor(FlowDefinitionRegistry flowRegistry) {
        return getFlowExecutorBuilder(flowRegistry).build();
    }

    @Bean
    FlowHandlerMapping flowHandlerMapping(FlowDefinitionRegistry flowRegistry) {
        FlowHandlerMapping mapping = new FlowHandlerMapping();
        mapping.setFlowRegistry(flowRegistry);
        mapping.setOrder(-1);
        return mapping;
    }

    @Bean
    FlowHandlerAdapter flowHandlerAdapter(FlowExecutor flowExecutor) {
        FlowHandlerAdapter adapter = new FlowHandlerAdapter();
        adapter.setFlowExecutor(flowExecutor);
        return adapter;
    }
}
```

`FlowHandlerMapping` resolves `/registration-flow` against the registry.
Its negative order lets Web Flow claim registered flow IDs before `RequestMappingHandlerMapping` evaluates ordinary controller routes.
`FlowHandlerAdapter` then resumes or starts the execution through the configured `FlowExecutor`.

## Happy-path flow test

Keep Jupiter tests outside the JUnit 3-based Web Flow harness and expose only the small operations and assertions they need.

```java
final class RegistrationFlowHarness extends AbstractXmlFlowExecutionTests {
    @Override
    protected FlowDefinitionResource getResource(FlowDefinitionResourceFactory resources) {
        return resources.createFileResource("src/main/webapp/WEB-INF/flows/registration/registration-flow.xml");
    }

    void start() {
        startFlow(new MockExternalContext());
    }

    void registration(Registration registration) {
        getFlowScope().put("registration", registration);
    }

    void signal(String eventId) {
        MockExternalContext context = new MockExternalContext();
        context.setEventId(eventId);
        resumeFlow(context);
    }

    void assertState(String stateId) {
        assertCurrentStateEquals(stateId);
    }

    void assertEnded() {
        assertFlowExecutionEnded();
    }

    void assertOutcome(String outcome) {
        assertFlowExecutionOutcomeEquals(outcome);
    }
}

class RegistrationFlowTests {
    private final RegistrationFlowHarness harness = new RegistrationFlowHarness();

    @Test
    void completesRegistration() {
        harness.start();
        harness.registration(validRegistration());
        harness.assertState("details");
        harness.signal("next");
        harness.assertState("review");
        harness.signal("confirm");
        assertAll(harness::assertEnded, () -> harness.assertOutcome("finished"));
    }
}
```

Add separate tests for validation retention, backtracking, recovery, and subflow outcomes only when those branches are part of the flow contract.

## Validation checklist

- Verify the start URL resolves the intended flow id.
- Verify every form posts to `flowExecutionUrl` with the intended `_eventId`.
- Verify forward navigation validates the scoped model and backward navigation follows the intended validation policy.
- Verify mapping order does not let an MVC controller shadow the flow id.
- Verify one happy path ends with the expected outcome.
- Verify technical recovery renders a stable state without swallowing expected business failures.

## Output contract

Return:

1. The flow XML, flow id, start URL, and event names
2. The scoped model, validation timing, and recovery contract
3. The registry, executor, handler mapping, adapter, and mapping order
4. The event-form shape and focused flow-execution test
5. Any subflow, scope-lifetime, validator, persistence, or error-recovery blocker

## References

- Open [references/web-flow-scopes.md](references/web-flow-scopes.md) when request, flash, view, flow, and conversation scope tradeoffs are the blocker.
- Open [references/web-flow-validation-and-exception-handling.md](references/web-flow-validation-and-exception-handling.md) when grouped validation, validator methods, or shared recovery behavior goes beyond the common path.
- Open [references/web-flow-execution-testing.md](references/web-flow-execution-testing.md) when backtracking, invalid input, exceptions, or subflow exits need dedicated tests.
