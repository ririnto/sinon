# Spring HATEOAS entity links and hypermedia support

Open this reference when links should derive from aggregate types, the application needs `EntityLinks`, or explicit `@EnableHypermediaSupport` activation is required instead of relying on the ordinary Boot starter path.

## Explicit hypermedia support

```java
@Configuration
@EnableHypermediaSupport(type = EnableHypermediaSupport.HypermediaType.HAL)
class HypermediaConfiguration {
}
```

Use explicit hypermedia support when the application is not already getting the required hypermedia setup from the ordinary Boot starter path.

## Entity links

```java
@Controller
@ExposesResourceFor(Order.class)
class OrderController {
    @GetMapping("/orders/{id}")
    OrderModel one(@PathVariable long id) {
        throw new UnsupportedOperationException();
    }
}

@Autowired
EntityLinks entityLinks;

Link self = entityLinks.linkToItemResource(Order.class, 1L).withSelfRel();
```

Use entity links when links should be derived from the exposed aggregate type instead of repeating controller method references in many places.

## Decision points

| Situation | Use |
| --- | --- |
| Boot starter already gives the required HAL setup | ordinary path in `SKILL.md` |
| Explicit hypermedia activation is required | `@EnableHypermediaSupport` |
| Links should derive from an exposed aggregate type | `EntityLinks` |
