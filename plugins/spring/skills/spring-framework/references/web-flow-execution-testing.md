# Spring Web Flow flow execution testing

Open this reference when state transitions themselves are the behavior under test and the ordinary happy-path assertions in `SKILL.md` are not enough.

Use flow execution tests when backtracking, invalid input, exception paths, or subflow exits are part of the contract.

## Test harness baseline

Spring Web Flow's official XML flow test support starts from `AbstractXmlFlowExecutionTests`. When the project standardizes on JUnit 5, keep the public test class in Jupiter style and delegate flow-harness operations to a helper subclass.

Define the booking service stub in the test source set. Treat it as application test code, not as a framework-provided helper.

```java
final class BookingServiceStub implements BookingService {
    private boolean failOnSave;

    void failOnSave() {
        this.failOnSave = true;
    }

    @Override
    public Booking save(Booking booking) {
        if (failOnSave) {
            throw new IllegalStateException("simulated save failure");
        }
        return booking;
    }
}
```

```java
class BookingFlowHarness extends AbstractXmlFlowExecutionTests {
    private final BookingServiceStub bookingService = new BookingServiceStub();

    @Override
    protected FlowDefinitionResource getResource(FlowDefinitionResourceFactory resourceFactory) {
        return resourceFactory.createFileResource("src/main/webapp/WEB-INF/flows/booking-flow.xml");
    }

    @Override
    protected void configureFlowBuilderContext(MockFlowBuilderContext builderContext) {
        builderContext.registerBean("bookingService", bookingService);
    }

    void start(MutableAttributeMap<Object> input, MockExternalContext context) {
        startFlow(input, context);
    }

    void signal(String stateId, String eventId, Booking booking) {
        setCurrentState(stateId);
        getFlowScope().put("booking", booking);
        MockExternalContext context = new MockExternalContext();
        context.setEventId(eventId);
        resumeFlow(context);
    }

    void registerPaymentSubflow(Flow paymentSubflow) {
        getFlowDefinitionRegistry().registerFlowDefinition(paymentSubflow);
    }

    BookingServiceStub bookingService() {
        return bookingService;
    }
}
```

Point the test at the flow XML first, then register every bean the flow evaluates through expressions such as `bookingService.save(...)`.

## Startup test shape

Use `startFlow(...)` when the behavior under test is flow initialization, required input, or the first rendered state.

```java
class BookingFlowExecutionTests {
    private final BookingFlowHarness harness = new BookingFlowHarness();

    @Test
    void startFlowShowsEnterDetails() {
        MutableAttributeMap<Object> input = new LocalAttributeMap<>();
        input.put("bookingId", "42");
        MockExternalContext context = new MockExternalContext();
        context.setCurrentUser("keith");
        harness.start(input, context);
        harness.assertCurrentStateEquals("enterDetails");
    }
}
```

## Forward transition test shape

Use `MockExternalContext` and set the event id explicitly before `resumeFlow(...)`.

```java
@Test
void enterDetailsNextMovesToConfirm() {
    harness.signal("enterDetails", "next", createTestBooking());
    harness.assertCurrentStateEquals("confirm");
}
```

Test the smallest meaningful transition sequence first, then add edge cases for backtracking, invalid input, or subflow exits.

## Invalid input and backtracking shapes

```java
@Test
void invalidInputStaysOnEnterDetails() {
    harness.signal("enterDetails", "next", invalidBooking());
    harness.assertCurrentStateEquals("enterDetails");
}

@Test
void confirmBackReturnsToEnterDetails() {
    harness.signal("confirm", "back", createTestBooking());
    harness.assertCurrentStateEquals("enterDetails");
}
```

Use the invalid-input path to prove validation timing and error retention. Use the backtracking path to prove that only the intended state survives backward navigation.

## Subflow exit shape

Register a mock subflow when the parent flow behavior depends on the child flow outcome.

```java
@Test
void collectPaymentPaidFinishesParentFlow() {
    harness.registerPaymentSubflow(createMockPaymentSubflow());
    harness.signal("collectPayment", "paid", createTestBooking());
    assertAll(
        () -> harness.assertFlowExecutionEnded(),
        () -> harness.assertFlowExecutionOutcomeEquals("finished")
    );
}
```

## Exception-path shape

```java
@Test
void saveBookingFailureGoesToTechnicalError() {
    harness.bookingService().failOnSave();
    harness.signal("saveBooking", "success", createTestBooking());
    harness.assertCurrentStateEquals("technicalError");
}
```

Use one exception-path test when a global transition, exception handler, or recovery state is part of the contract.

## JUnit 5 compatibility note

`AbstractXmlFlowExecutionTests` inherits from a legacy `TestCase` base class, but a project can still keep the public test surface in JUnit 5 style by delegating the harness operations through a helper subclass as shown above. Keep one assertion per `assertAll` line when multiple flow-outcome checks belong to the same scenario.

## Gotchas

- Do not call `resumeFlow(requestContext())`; the official API expects `resumeFlow(context)` with a `MockExternalContext`.
- Do not skip `getResource(...)` or bean registration when the flow evaluates services, validators, or subflow dependencies.
- Do not start with a full end-to-end journey when one state transition is the behavior under test.
- Do not skip negative-path assertions for invalid input, backtracking, or exception handling.
- Do not treat subflow entry and exit mapping as implicitly correct without a dedicated assertion.
