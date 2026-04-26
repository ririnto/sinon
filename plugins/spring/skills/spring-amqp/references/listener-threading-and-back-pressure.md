# Listener threading and back pressure

Open this reference when the ordinary listener-container path in [SKILL.md](../SKILL.md) is not enough and the blocker is consumer threading or back pressure.

## Consumer-threading blocker

**Problem:** consumer threading, back pressure, or callback execution shape is the real bottleneck rather than broker topology.

**Solution:** treat threading decisions as part of listener-container design, not business logic.

```java
@Bean
SimpleRabbitListenerContainerFactory threadedFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory, TaskExecutor taskExecutor) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    configurer.configure(factory, connectionFactory);
    factory.setTaskExecutor(taskExecutor);
    factory.setPrefetchCount(20);
    return factory;
}
```

## Decision points

| Situation | First choice |
| --- | --- |
| the problem is container thread behavior | revisit container factory and threading policy |

## Pitfalls

- Do not move back-pressure handling into business code when the real issue is container design.
- Verify queue ordering and downstream saturation before widening consumer execution.
