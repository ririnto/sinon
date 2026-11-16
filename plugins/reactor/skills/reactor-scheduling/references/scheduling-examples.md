# Reactor Scheduling Examples

## Blocking boundary at the source

```java
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
final class BlockingLookup {
    Mono<String> readUser(String userId) {
        return Mono.fromCallable(() -> blockingLookup(userId))
            .subscribeOn(Schedulers.boundedElastic())
            .map(String::trim);
    }
    private String blockingLookup(String userId) {
        return "user-" + userId;
    }
}
```

## Downstream handoff with `publishOn(...)`

```java
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
final class PublishOnExample {
    Flux<String> process() {
        return Flux.just("a", "b", "c")
            .map(String::toUpperCase)
            .publishOn(Schedulers.parallel())
            .map(value -> value + "-done");
    }
}
```

## Different upstream and downstream execution contexts

```java
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
final class MixedExecutionExample {
    Mono<String> loadAndTransform(String userId) {
        return Mono.fromCallable(() -> blockingLookup(userId))
            .subscribeOn(Schedulers.boundedElastic())
            .publishOn(Schedulers.parallel())
            .map(String::toUpperCase);
    }
    private String blockingLookup(String userId) {
        return "user-" + userId;
    }
}
```

## Context survives thread hops

```java
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
final class ContextAcrossThreads {
    Mono<String> handle(String input) {
        return Mono.deferContextual(context -> Mono.just(context.get("requestId") + ":" + input))
            .publishOn(Schedulers.parallel())
            .contextWrite(Context.of("requestId", "req-42"));
    }
}
```
