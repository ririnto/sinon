# Multi-broker variants

Open this reference when the ordinary single-broker path in [SKILL.md](../SKILL.md) is not enough and the blocker is isolating multiple broker connections, templates, or listener factories.

## Multi-broker blocker

**Problem:** different workloads must publish to or consume from different broker connections.

**Solution:** isolate connection factories, templates, and listener factories by broker role.

- Keep each broker path explicit in bean naming and configuration.
- Avoid sharing routing-key or queue-name assumptions across brokers unless the contract is truly identical.
- Keep each listener factory bound to the broker connection it actually serves.

```java
@Bean
RabbitTemplate billingRabbitTemplate(@Qualifier("billingConnectionFactory") ConnectionFactory connectionFactory) {
    return new RabbitTemplate(connectionFactory);
}

@Bean
SimpleRabbitListenerContainerFactory billingListenerFactory(@Qualifier("billingConnectionFactory") ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    return factory;
}
```

## Decision points

| Situation | First choice |
| --- | --- |
| one broker serves all workloads | stay on the ordinary path |
| publishers and listeners must isolate broker credentials or topology | separate broker-specific templates and listener factories |

## Pitfalls

- Do not hide multiple brokers behind ambiguous bean names.
- Do not reuse one listener factory across incompatible broker paths.
- Do not assume topology names are portable across brokers without an explicit contract.

## Validation rule

Verify every template, container factory, and topology bean is wired to the intended broker connection before treating the multi-broker split as safe.
