# Configuration and test examples

## Example 1: Two-step flow

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

## Example 2: Happy-path flow test

```java
final class RegistrationFlowHarness extends AbstractXmlFlowExecutionTests {
    /**
     * Locates the flow definition XML that this harness executes.
     */
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
