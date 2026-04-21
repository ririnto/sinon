# Spring Framework async executor registration

Open this reference when the common path in `SKILL.md` is not enough and the blocker is async exception handling, completion coordination, or `TaskDecorator` for thread-local propagation.

## Async exception handling

Exceptions from `@Async` methods that return `void` do not propagate to the caller. Handle those failures with an `AsyncUncaughtExceptionHandler`:

```java
@Configuration
@EnableAsync
class AppConfig implements AsyncConfigurer {
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("Async error in {}: {}", method.getName(), ex.getMessage());
        };
    }
}
```

Use this when a `void`-returning async method can fail and the caller has no `Future` or `CompletableFuture` to inspect.

## Completion coordination with `Future` and `CompletableFuture`

`@Async` methods return `void`, `Future<V>`, or `CompletableFuture<V>`. Use `Future` or `CompletableFuture` when the caller must wait or recover from failure:

```java
@Async
CompletableFuture<Item> findById(Long id) {
    return CompletableFuture.completedFuture(repository.findById(id));
}
```

Caller:

```java
CompletableFuture<Item> future = notifier.findById(id);
Item item = future.get(10, TimeUnit.SECONDS);
```

Use `thenCompose` or `thenApply` to chain follow-up work without blocking the caller thread.

## TaskDecorator for thread-local propagation

Use a `TaskDecorator` when MDC, security context, or other thread-local state must cross the executor boundary:

```java
@Bean
ThreadPoolTaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(runnable -> {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            }
            try {
                runnable.run();
            }
            finally {
                MDC.clear();
            }
        };
    });
    executor.initialize();
    return executor;
}
```

Open [aspectj-ltw.md](aspectj-ltw.md) when the task requires load-time weaving, field or constructor join points, or `@Configurable` domain object injection.
