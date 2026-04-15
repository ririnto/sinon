---
name: spring-webflux
description: >-
  Use this skill when the user asks to "build a reactive Spring endpoint", "use Mono or Flux in Spring", "choose WebFlux controllers or functional routes", "use WebClient", or needs guidance on Spring WebFlux patterns.
---

# Spring WebFlux

## Overview

Use this skill to design reactive Spring endpoints and clients with clear `Mono`/`Flux` semantics, cancellation-aware composition, and non-blocking boundaries. The common case is first proving the endpoint or client really benefits from reactive composition, then choosing the smallest readable WebFlux shape. Keep blocking work out of the reactive path and keep stream semantics honest.

## Use This Skill When

- You are designing reactive controllers, functional routes, or `WebClient` integrations.
- You need to choose between `Mono` and `Flux`.
- You need a default WebFlux endpoint or client shape you can paste into a codebase.
- Do not use this skill when the boundary is blocking servlet MVC rather than reactive.

## Common-Case Workflow

1. Confirm that the endpoint or client actually benefits from reactive composition.
2. Choose annotated controllers or functional routes based on the codebase style.
3. Use `Mono` for one response and `Flux` for streams.
4. Keep blocking work out of the reactive flow and keep outbound client customization explicit by case.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

## First Runnable Commands or Code Shape

Start with one reactive controller method:

```java
@RestController
@RequestMapping("/events")
class EventController {

    private final EventService eventService;

    EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<EventResponse> stream() {
        return eventService.streamEvents();
    }
}
```

---

*Applies when:* the endpoint returns a real stream rather than one finite response.

## Ready-to-Adapt Templates

Reactive controller:

```java
@RestController
@RequestMapping("/events")
class EventController {

    private final EventService eventService;

    EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<EventResponse> stream() {
        return eventService.streamEvents();
    }
}
```

---

*Applies when:* the codebase already uses annotated controllers and the response is truly reactive.

Reactive client:

```java
class RemoteClient {
    private final WebClient webClient;

    RemoteClient(WebClient webClient) {
        this.webClient = webClient;
    }

    Mono<RemoteDto> fetch() {
        return webClient.get()
                .uri("/remote")
                .retrieve()
                .bodyToMono(RemoteDto.class);
    }
}
```

---

*Applies when:* you need a reactive outbound HTTP call with no special per-client customization.

Functional route:

```java
@Bean
RouterFunction<ServerResponse> routes(Handler handler) {
    return RouterFunctions.route(RequestPredicates.GET("/health"), handler::health);
}
```

---

*Applies when:* the codebase already prefers functional routing or the route DSL is a better fit.

Request-level client customization:

```java
Mono<RemoteDto> fetch(WebClient client) {
    return client.get()
            .uri("/items")
            .headers(headers -> headers.setBearerAuth("token"))
            .retrieve()
            .bodyToMono(RemoteDto.class);
}
```

---

*Applies when:* one request needs extra auth or headers but the shared client defaults are still correct.

## Validate the Result

Validate the common case with these checks:

- `Mono` is used for one logical response and `Flux` only for real stream contracts
- blocking work is not hidden inside the reactive path
- annotated controllers or functional routes are chosen for codebase fit rather than novelty
- `WebClient` customization is applied at the right layer: shared policy, one client, or one request

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| endpoint shape, `Mono` versus `Flux`, or route-style tradeoffs | `./references/webflux-patterns.md` |
| `WebClient`, codecs, streaming, or advanced client wiring | `./references/webflux-client-config.md` |

## Invariants

- MUST preserve non-blocking boundaries.
- SHOULD choose `Mono` for one value and `Flux` for streams.
- MUST not introduce reactive complexity without a real reason.
- SHOULD keep reactive composition readable and cancellation-aware.
- SHOULD apply outbound client rules by case rather than mixing shared and per-client concerns together.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| wrapping blocking JDBC or HTTP code in `Mono` without fixing the blocking source | the code still blocks under a reactive surface | isolate or remove the blocking source explicitly |
| using WebFlux only because it seems newer | the boundary gets more complex without benefit | prove the reactive need first |
| returning `Flux` where one value is the real contract | the API promises a stream it does not need | keep one-value contracts as `Mono` |

## Scope Boundaries

- Activate this skill for:
  - reactive Spring HTTP behavior
  - `Mono` / `Flux` endpoint and client design
  - `WebClient`, streaming, and non-blocking boundaries
- Do not use this skill as the primary source for:
  - servlet-specific MVC behavior
  - generic persistence rules
  - framework-agnostic coroutine design detached from WebFlux
