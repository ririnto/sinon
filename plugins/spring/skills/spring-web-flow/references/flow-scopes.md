# Spring Web Flow scopes

Open this reference when the ordinary wizard flow in `SKILL.md` is not enough and the task needs scope tradeoffs, scope searching, or longer-lived conversation state.

## Scope decisions

- `requestScope`: data needed only for the current request.
- `flashScope`: data that should survive one redirect or transition.
- `viewScope`: state tied to the current rendered view.
- `flowScope`: state that must survive the whole flow execution.
- `conversationScope`: state shared across nested flows or a longer conversation.

Prefer the narrowest scope that preserves the required state.

## Scope boundary

- Use `flowScope` for the ordinary multi-step form object.
- Use `viewScope` when the data belongs only to the current rendered page.
- Use `conversationScope` only when nested flows or a longer conversation genuinely share state.

## Concrete scope example

Use this flow when one booking wizard needs page-local hints, whole-flow form state, and one value shared with a nested payment subflow.

```xml
<flow xmlns="http://www.springframework.org/schema/webflow"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://www.springframework.org/schema/webflow https://www.springframework.org/schema/webflow/spring-webflow.xsd">

    <on-start>
        <set name="flowScope.booking" value="new com.example.Booking()"/>
        <set name="conversationScope.customerId" value="requestParameters.customerId"/>
    </on-start>

    <view-state id="enterTraveler" model="flowScope.booking">
        <on-entry>
            <set name="viewScope.stepTitle" value="'Traveler details'"/>
            <set name="requestScope.availableSeats" value="bookingService.availableSeats(conversationScope.customerId)"/>
        </on-entry>
        <transition on="next" to="review">
            <set name="flashScope.banner" value="'Traveler details saved'"/>
        </transition>
    </view-state>

    <view-state id="review" model="flowScope.booking">
        <transition on="pay" to="collectPayment"/>
    </view-state>

    <subflow-state id="collectPayment" subflow="payment-flow">
        <input name="customerId" value="conversationScope.customerId"/>
        <input name="booking" value="flowScope.booking"/>
        <transition on="paid" to="finished"/>
    </subflow-state>

    <end-state id="finished" view="booking/complete"/>
</flow>
```

### Scope roles in the example

| Scope | Why it fits here |
| --- | --- |
| `requestScope.availableSeats` | recalculated for the current render only |
| `flashScope.banner` | survives the next transition and then disappears |
| `viewScope.stepTitle` | belongs only to the current rendered page |
| `flowScope.booking` | survives the whole booking wizard |
| `conversationScope.customerId` | shared with the nested payment subflow |

## Gotchas

- Do not put large mutable graphs into `conversationScope` casually.
- Do not use `conversationScope` when `flowScope` is sufficient.
- Do not assume every scope survives the same redirects, backtracking, or nested-flow boundaries.
