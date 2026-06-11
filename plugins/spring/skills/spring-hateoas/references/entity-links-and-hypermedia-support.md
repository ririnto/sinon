# Spring HATEOAS entity links and hypermedia support

> [!IMPORTANT]
>
> Prefer `WebMvcLinkBuilder.linkTo()` / `WebFluxLinkBuilder.linkTo()` with method references. Use this reference only when maintaining existing code that already depends on `@ExposesResourceFor` or `EntityLinks`.

Open this reference when links should derive from aggregate types, the application needs `EntityLinks`, or explicit `@EnableHypermediaSupport` activation is required instead of relying on the ordinary Boot starter path.

## Explicit hypermedia support

```java
@Configuration
@EnableHypermediaSupport(type = EnableHypermediaSupport.HypermediaType.HAL)
class HypermediaConfiguration {
}
```

Use explicit hypermedia support when the application is not already getting the required hypermedia setup from the ordinary Boot starter path.

The ordinary Boot starter path already enables the common HAL setup. Open this reference only when that default is insufficient or when aggregate-type-based link derivation is clearer than assembler-owned controller links.

## Entity links controller setup

```java
@Controller
@ExposesResourceFor(Order.class)
@RequestMapping("/orders")
class OrderController {

    @GetMapping
    ResponseEntity<?> orders() { ... }

    @GetMapping("/{id}")
    ResponseEntity<?> order(@PathVariable Long id) { ... }
}
```

`@ExposesResourceFor(Order.class)` declares which entity type the controller manages. The type-level `@RequestMapping` is the collection resource base. The method-level mapping extending with an identifier is the item resource.

## Entity links usage

```java
@Autowired
EntityLinks entityLinks;

Link self = entityLinks.linkToItemResource(Order.class, 1L).withSelfRel();
Link collection = entityLinks.linkToCollectionResource(Order.class).withRel("orders");
```

Use entity links when links should be derived from the exposed aggregate type instead of repeating controller method references in many places.

## Assembler versus EntityLinks

| Situation | Use |
| --- | --- |
| One representation owns its own local link rules | assembler in `SKILL.md` |
| Many places need the same aggregate-derived canonical link | `EntityLinks` |
| Hypermedia configuration is not already activated by Boot | `@EnableHypermediaSupport` |

## Decision points

| Situation | Use |
| --- | --- |
| Boot starter already gives the required HAL setup | ordinary path in `SKILL.md` |
| Explicit hypermedia activation is required | `@EnableHypermediaSupport` |
| Links should derive from an exposed aggregate type | `EntityLinks` |

## Validation rule

Verify that aggregate-derived links stay consistent with the controller mapping actually exposed to clients before replacing assembler-owned links with `EntityLinks`.
