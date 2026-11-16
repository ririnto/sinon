# Output and configuration shapes

## Output and configuration shapes

### Queue and exchange naming shape

```text
orders
orders.exchange
orders.created
orders.dlq
```

### Publish shape

```java
rabbitTemplate.convertAndSend("orders.exchange", "orders.created", event);
```

### Listener shape

```java
@RabbitListener(queues = "orders")
void handle(OrderCreated event) {
    orderService.process(event);
}
```

### Container factory shape

```java
SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
```

### Boot auto-configuration hooks

Boot 4.1.x auto-configures several customization points.
Declare these beans to customize the auto-configured defaults without replacing the entire infrastructure.

| Bean | Effect |
| --- | --- |
| `ConnectionFactoryCustomizer` | Customize the underlying `CachingConnectionFactory` properties |
| `ConnectionNameStrategy` | Name connections from application context |
| `RabbitTemplateCustomizer` | Apply additive customizations to the auto-configured `RabbitTemplate` |
| `RabbitTemplateConfigurer` | Build additional `RabbitTemplate` instances with auto-configured settings |
| `RabbitTemplateRetrySettingsCustomizer` | Programmatic retry customization for the template |
| `RabbitListenerRetrySettingsCustomizer` | Programmatic retry customization for listeners |
| `RabbitTemplateObservationConvention` | Custom observation naming for template operations |
| `RabbitListenerObservationConvention` | Custom observation naming for listener operations |
