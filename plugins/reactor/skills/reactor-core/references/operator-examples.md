# Reactor Core Examples

## Ordinary `Mono` contract with blocking bridge and explicit failure path

```java
import java.io.IOException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
final class UserLookupService {
    Mono<UserView> loadUser(String userId) {
        return Mono.fromCallable(() -> fetchUser(userId))
            .subscribeOn(Schedulers.boundedElastic())
            .switchIfEmpty(Mono.error(new IllegalStateException("Missing user: " + userId)))
            .map(user -> new UserView(user.id(), user.name()))
            .onErrorMap(IOException.class, error -> new IllegalStateException("User lookup failed", error));
    }
    private User fetchUser(String userId) throws IOException {
        return new User(userId, "Ada");
    }
    record User(String id, String name) {}
    record UserView(String id, String name) {}
}
```

One scheduler hop offloads the blocking call to `boundedElastic`.
Multi-hop placement or custom scheduler design is scheduler-specific work outside this core pipeline path.

## `Flux` pipeline with ordered async fan-out and empty fallback

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
final class ActivityService {
    Flux<String> recentActions(String userId) {
        return findActionIds(userId)
            .concatMap(this::fetchAction)
            .filter(action -> !action.isBlank())
            .switchIfEmpty(Flux.just("NO_ACTIONS"));
    }
    private Flux<String> findActionIds(String userId) {
        return Flux.just("login", "purchase", "logout");
    }
    private Mono<String> fetchAction(String actionId) {
        return Mono.just(actionId.toUpperCase());
    }
}
```

## Ordinary `Context` usage for request metadata

```java
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
final class TraceAwareHandler {
    Mono<String> handle(String input) {
        return Mono.deferContextual(context -> Mono.just(context.get("traceId") + ":" + input))
            .contextWrite(Context.of("traceId", "trace-123"));
    }
}
```

## `switchMap` for cancellation-aware fan-out

```java
import reactor.core.publisher.Flux;
import java.time.Duration;
final class SearchAsYouType {
    Flux<String> search(Flux<String> queries) {
        return queries.switchMap(query ->
            fetchResults(query).take(Duration.ofMillis(200))
        );
    }
    private Flux<String> fetchResults(String query) {
        return Flux.just(query + "-result1", query + "-result2");
    }
}
```

`switchMap` cancels the previous inner publisher when a new trigger arrives.
Use it for latest-win scenarios like search-as-you-type or live configuration refresh.

## `timeout` with fallback

```java
import java.time.Duration;
import reactor.core.publisher.Mono;
final class BoundedCall {
    Mono<String> fetchWithTimeout() {
        return remoteCall()
            .timeout(Duration.ofSeconds(3), Mono.just("fallback-value"));
    }
    private Mono<String> remoteCall() {
        return Mono.just("response").delayElement(Duration.ofMillis(100));
    }
}
```

## `repeat` for completion-side re-subscription

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
final class PollingService {
    Flux<String> pollThreeTimes() {
        return fetchStatus()
            .repeat(2)
            .filter(status -> "DONE".equals(status))
            .take(1);
    }
    private Mono<String> fetchStatus() {
        return Mono.just("PENDING");
    }
}
```

`repeat` re-subscribes on completion (not error).
For finite work, bound re-subscriptions with `repeat(n)` or a terminating `repeatWhen(...)` companion.
Use `take(n)` or `takeUntil(...)` when emitted values provide the required stopping condition; empty completions do not satisfy them.
For a long-lived stream, define cancellation and lifecycle ownership, and pace re-subscriptions when needed.

## `handle` for conditional single-signal emission

```java
import reactor.core.publisher.Flux;
final class ConditionalEmission {
    Flux<Integer> positive(Flux<Integer> source) {
        return source.handle((value, sink) -> {
            if (value > 0) {
                sink.next(value);
            }
        });
    }
}
```

The `handle` callback may emit zero or one value per input element.
Values less than or equal to zero are silently filtered (no signal emitted).
Use `flatMapIterable(...)` or `flatMap(...)` when one input must produce multiple values.

## `flatMapMany` for Mono-to-Flux cardinality change

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;
final class BatchExpand {
    Flux<String> expandBatch(Mono<List<String>> batchLoader) {
        return batchLoader.flatMapMany(Flux::fromIterable);
    }
}
```

Use `flatMapMany` when a `Mono<T>` produces a collection that should be emitted as individual elements in a `Flux`.
