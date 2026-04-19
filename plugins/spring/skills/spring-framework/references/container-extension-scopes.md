# Spring Framework container extension points and scopes

Open this reference when the ordinary bean-wiring path in [SKILL.md](../SKILL.md) is not enough and the blocker is container extension points, custom scope registration, advanced listener infrastructure, or `@Configuration` lite-mode behavior.

## BeanFactoryPostProcessor blocker

Use a `BeanFactoryPostProcessor` when bean definitions must change before any application beans are instantiated.

```java
@Component
class PlaceholderPrefixPostProcessor implements BeanFactoryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }
}
```

Keep this hook focused on bean-definition metadata, not on live bean instances.

## BeanPostProcessor blocker

Use a `BeanPostProcessor` when bean instances must be wrapped or adjusted after creation.

```java
@Component
class TimingBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        return bean;
    }
}
```

Keep post-processors explicit because they can change behavior for many beans at once.

## Custom scope blocker

Use a custom scope only when singleton and prototype are both wrong and the lifecycle boundary is application-specific.

```java
beanFactory.registerScope("tenant", new SimpleThreadScope());
```

Register the scope first, then use it deliberately on the beans that truly depend on that lifecycle.

## `@Configuration` lite-mode blocker

Use full `@Configuration` when `@Bean` methods must call each other through the container-managed proxy. A plain `@Component` with `@Bean` methods runs in lite mode and behaves like normal factory methods.

```java
@Component
class LiteConfig {
    @Bean
    InventoryService inventoryService() {
        return new InventoryService(repository());
    }

    @Bean
    InventoryRepository repository() {
        return new InventoryRepository();
    }
}
```

Use lite mode only when direct Java method calls are acceptable and inter-bean proxy semantics are not required.

## Advanced listener blocker

Use lower-level listener infrastructure only when basic `ApplicationListener` or `@EventListener` is not enough for ordering, filtering, or generic-type control.

## Decision points

| Situation | First check |
| --- | --- |
| Need to change bean definitions before creation | use `BeanFactoryPostProcessor` |
| Need to wrap many beans after creation | use `BeanPostProcessor` |
| Bean lifecycle is neither singleton nor prototype | register a custom scope |
| `@Bean` methods behave like direct factory calls | verify whether lite mode is the real issue |
| Basic event listeners are not expressive enough | reach for advanced listener infrastructure |
