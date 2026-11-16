# Flow setup

## Gateway shape

```java
@MessagingGateway(defaultRequestChannel = "orders.input")
interface OrderGateway {
    void submit(OrderCommand command);
}
```

## Basic flow shape

For basic flow shape, see [example 5](configuration-examples.md#example-5-basic-flow-shape).

This ordinary DSL chain covers the three most common endpoint types in one place: transformer, filter, and service activator.

## Error channel shape

For error channel shape, see [example 6](configuration-examples.md#example-6-error-channel-shape).

Use the default `errorChannel` for the ordinary baseline.
