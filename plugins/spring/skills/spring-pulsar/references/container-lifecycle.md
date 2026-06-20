# Spring Pulsar message listener container lifecycle

Open this reference when listener containers must be paused, resumed, or configured for startup failure handling.

## Pausing and resuming

Obtain the container instance from `PulsarListenerEndpointRegistry` and invoke pause or resume.

```java
@Autowired
PulsarListenerEndpointRegistry registry;

void pauseListener() {
    PulsarMessageListenerContainer container = registry.getListenerContainer("my-listener-id");
    container.pause();
}

void resumeListener() {
    PulsarMessageListenerContainer container = registry.getListenerContainer("my-listener-id");
    container.resume();
}
```

The `id` attribute on `@PulsarListener` sets the listener container id for lookup.

## Handling startup failures

By default, any failure during container startup is re-thrown and the application fails to start.
Adjust with `StartupFailurePolicy`:

- `Stop` (default) -- log and re-throw, stopping the application.
- `Continue` -- log and leave the container in a non-running state without stopping the application.
- `Retry` -- log and retry asynchronously.
  - The default retry behavior retries 3 times with a 10-second delay between attempts.
  - A custom retry template can be specified.

### Configuration with Spring Boot

```java
@Bean
PulsarContainerFactoryCustomizer<ConcurrentPulsarListenerContainerFactory<?>> startupCustomizer() {
    return factory -> {
        var properties = factory.getContainerProperties();
        properties.setStartupFailurePolicy(StartupFailurePolicy.RETRY);
        properties.setRetryTemplate(retryTemplate());
    };
}
```

## Decision points

- Use pause and resume when the application must temporarily stop consuming from a topic without shutting down.
- Use `StartupFailurePolicy.CONTINUE` or `RETRY` when a listener connects to a topic created after startup.
- Verify the listener `id` attribute is set when pause/resume or startup failure policies are used.
