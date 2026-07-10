# Spring Web Flow validation and exception handling

Open this reference when the common path is not enough and the blocker is state-specific model validation, application validator wiring, or exception recovery local to one action.

## State-specific model validator

For a model named `registration`, expose a `registrationValidator` bean.
Web Flow invokes `validateDetails` when leaving the `details` view state with validation enabled.

```java
@Component("registrationValidator")
class RegistrationValidator {
    void validateDetails(Registration registration, ValidationContext context) {
        if (!StringUtils.hasText(registration.email())) {
            context.getMessageContext().addMessage(new MessageBuilder().error().source("email").code("email.required").build());
        }
    }
}
```

Name validation methods after the state whose model is being committed.
Put reusable domain invariants on the model or service boundary; keep flow validators focused on step-specific completeness.

## Local action recovery

Handle a known application exception at the action that can raise it instead of sending it through the global technical-error state.

```xml
<action-state id="saveRegistration">
    <evaluate expression="registrationService.save(registration)"/>
    <transition on="success" to="finished"/>
    <transition on-exception="com.example.DuplicateRegistrationException" to="review">
        <set name="flashScope.saveError" value="'This registration already exists'"/>
    </transition>
</action-state>
```

Reserve the global recovery state in `SKILL.md` for unexpected failures that cannot be represented as a normal validation or business event.

## Gotchas

- Do not validate backward navigation unless the product flow requires it.
- Do not duplicate the same validation rule in the form object, flow validator, and service.
- Do not route expected conflicts through a generic technical-error state.
- Do not catch broad exceptions at one action when the flow-level recovery transition already owns unexpected failures.
