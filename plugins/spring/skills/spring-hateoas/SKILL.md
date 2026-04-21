---
name: "spring-hateoas"
description: "Use this skill when building Spring hypermedia representations with links, assemblers, HAL output, affordances, and paged models for server responses."
metadata:
  title: "Spring HATEOAS"
  official_project_url: "https://spring.io/projects/spring-hateoas"
  reference_doc_urls:
    - "https://docs.spring.io/spring-hateoas/docs/current/reference/html/"
  compatibility_note: "Keep examples aligned with the Spring HATEOAS version already chosen in the project when docs and plugin listings differ slightly."
  version: "3.0.3"
---

Use this skill when building Spring hypermedia representations with links, assemblers, HAL output, affordances, and paged models for server responses.

## Boundaries

Use `spring-hateoas` for link modeling, representation assembly, hypermedia media types, and affordance exposure on the server side.

- Use narrower guidance when the task is about publishing API documentation from tests.
- Keep transport controllers thin. Hypermedia shape should live in assemblers or representation processors, not spread through controller methods.

## Common path

The ordinary Spring HATEOAS job is:

1. Start from the resource representation the client should see, not from the persistence entity.
2. Build links through an assembler so link rules stay consistent across endpoints.
3. Expose collection and page-level links separately from item-level links.
4. Pick one hypermedia media type, usually HAL, and keep it stable for the module.
5. Add a test that proves the response contains the required `_links` and embedded data shape.

## Surface map

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| Plain HAL item and collection responses | one controller returns `EntityModel` or `CollectionModel` | stay in `SKILL.md` |
| Page navigation and page metadata | clients depend on `prev`/`next`/`first`/`last` or strict page metadata semantics | open [references/pagedmodel-navigation-and-page-metadata.md](references/pagedmodel-navigation-and-page-metadata.md) |
| HAL-FORMS or affordances | clients need action metadata or `_templates` | open [references/hal-forms-and-affordances.md](references/hal-forms-and-affordances.md) |
| Aggregate-type link derivation | explicit hypermedia activation or `EntityLinks` is the blocker | open [references/entity-links-and-hypermedia-support.md](references/entity-links-and-hypermedia-support.md) |
| Cross-cutting link enrichment | one shared rule must enrich many models after assembly | open [references/representation-processors.md](references/representation-processors.md) |
| Reverse-proxy link correctness | generated host, scheme, or base path is wrong | open [references/forwarded-headers-and-proxy-configuration.md](references/forwarded-headers-and-proxy-configuration.md) |
| Problem Details error payloads | the error path needs `application/problem+json` | open [references/problem-details-error-representations.md](references/problem-details-error-representations.md) |

## Representation decisions

| Situation | Use |
| --- | --- |
| One item with links | `EntityModel<T>` |
| Collection with top-level links | `CollectionModel<T>` |
| Page result with navigation metadata | `PagedModel<T>` |

Keep link relation names stable and model the representation type the client actually consumes rather than returning domain entities directly.

## Dependency baseline

Use the Boot starter for ordinary HATEOAS work.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-hateoas</artifactId>
    </dependency>
</dependencies>
```

The Boot starter is the ordinary activation path. Open the entity-links reference only when the application needs explicit `@EnableHypermediaSupport`, `EntityLinks`, or aggregate-type-based link derivation.

### Feature-to-artifact map

| Need | Artifact |
| --- | --- |
| HAL item, collection, and paged responses | `spring-boot-starter-hateoas` |
| HAL-FORMS and affordances | `spring-boot-starter-hateoas` |
| EntityLinks and explicit hypermedia activation | `spring-boot-starter-hateoas` |

## First safe configuration

### First safe commands

```bash
./mvnw test -Dtest=OrderRepresentationTests
```

```bash
./gradlew test --tests OrderRepresentationTests
```

### HAL-oriented response expectation

```text
Content-Type: application/hal+json
```

### Paged collection response shape

```json
{
  "_embedded": {},
  "_links": {},
  "page": {
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "number": 0
  }
}
```

Default to HAL unless the project already committed to another hypermedia format.

## Coding procedure

1. Model the representation type explicitly and avoid returning JPA entities or domain aggregates directly.
2. Use `RepresentationModelAssembler` for item and collection representations so link logic is centralized.
3. Build links from controller methods rather than hand-written paths wherever practical.
4. Add affordances only when clients will actually act on them.
5. Keep link relation names stable once clients depend on them.
6. Test both single-resource and collection-resource responses for the expected link set.

## Processor boundary

Use an assembler for resource-specific links and use a `RepresentationModelProcessor` only when one cross-cutting rule must enrich many representations the same way.

Keep aggregate-type-based link derivation separate from ordinary assembler work. Open the entity-links reference only when controller-method links are no longer the right source of truth.

## Implementation examples

### Representation model and assembler

```java
class OrderModel extends RepresentationModel<OrderModel> {
    private final long id;
    private final String status;

    OrderModel(long id, String status) {
        this.id = id;
        this.status = status;
    }
}

class OrderModelAssembler implements RepresentationModelAssembler<Order, OrderModel> {
    @Override
    public OrderModel toModel(Order order) {
        OrderModel model = new OrderModel(order.id(), order.status());
        model.add(linkTo(methodOn(OrderController.class).one(order.id())).withSelfRel());
        model.add(linkTo(methodOn(OrderController.class).all()).withRel("orders"));
        return model;
    }

    @Override
    public CollectionModel<OrderModel> toCollectionModel(Iterable<? extends Order> orders) {
        CollectionModel<OrderModel> models = RepresentationModelAssembler.super.toCollectionModel(orders);
        models.add(linkTo(methodOn(OrderController.class).all()).withSelfRel());
        return models;
    }
}
```

### Controller using the assembler

```java
@RestController
class OrderController {
    private final OrderService service;
    private final OrderModelAssembler assembler;

    OrderController(OrderService service, OrderModelAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    @GetMapping("/orders/{id}")
    OrderModel one(@PathVariable long id) {
        return assembler.toModel(service.find(id));
    }

    @GetMapping("/orders")
    CollectionModel<OrderModel> all() {
        return assembler.toCollectionModel(service.findAll());
    }
}
```

### Paged model assembly

```java
PagedModel<OrderModel> model = pagedResourcesAssembler.toModel(page, assembler);
```

### Affordance shape

```java
Link self = linkTo(methodOn(OrderController.class).one(order.id())).withSelfRel();
Link update = self.andAffordance(afford(methodOn(OrderController.class).update(order.id(), null)));
```

Use affordances only when clients or generated UI flows will actually consume them.

### Hypermedia test shape

```java
@WebMvcTest(OrderController.class)
class OrderRepresentationTests {
    @Autowired
    MockMvc mvc;

    @Test
    void orderResponseContainsSelfLink() throws Exception {
        mvc.perform(get("/orders/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._links.self.href").exists());
    }
}
```

## Output and configuration shapes

### Entity model shape

```json
{
  "id": 1,
  "status": "CREATED",
  "_links": {
    "self": {
      "href": "/orders/1"
    },
    "orders": {
      "href": "/orders"
    }
  }
}
```

### Collection model link shape

```json
{
  "_links": {
    "self": {
      "href": "/orders"
    }
  }
}
```

### Stable relation name shape

```text
self
orders
```

## Testing checklist

- Verify item responses include the required `self` link and any collection or action links the client expects.
- Verify collection and paged responses include navigation links and page metadata where applicable.
- Verify link relations remain stable across refactors.
- Verify affordances appear only on representations that should expose those actions.
- Verify the selected media type is the one actually produced by the endpoint.

## Failure classification

- Treat missing `_links`, broken relation names, or malformed representation shapes as contract failures.
- Treat wrong host, scheme, or base path in generated links as deployment or proxy-configuration failures.
- Treat absent affordances or HAL-FORMS templates as capability-shape failures when clients depend on them.

## Production checklist

- Keep link relation names and URI templates stable after clients are published.
- Avoid exposing links for actions the current user cannot actually perform unless the API contract explicitly allows it.
- Keep one canonical hypermedia media type per module to reduce client ambiguity.
- Ensure reverse proxy or base-path configuration does not break generated links.
- Treat assembler tests as part of the API compatibility surface.

## Output contract

Return:

1. The chosen representation type and why it fits the endpoint contract
2. The assembler or processor shape that owns link generation
3. The stable link relations clients depend on
4. The selected hypermedia media type and any affordance or HAL-FORMS decision
5. The test shape proving the required `_links` and embedded data
6. Any blocker that requires paged navigation, entity links, processor-based enrichment, or proxy-aware link rewriting

## References

- Open [references/hal-forms-and-affordances.md](references/hal-forms-and-affordances.md) when plain HAL links are not enough and the representation must advertise action metadata or HAL-FORMS templates.
- Open [references/representation-processors.md](references/representation-processors.md) when one cross-cutting rule must enrich many models with the same extra links or metadata.
- Open [references/entity-links-and-hypermedia-support.md](references/entity-links-and-hypermedia-support.md) when links should derive from aggregate types, the app needs `EntityLinks`, or explicit `@EnableHypermediaSupport` configuration is required.
- Open [references/pagedmodel-navigation-and-page-metadata.md](references/pagedmodel-navigation-and-page-metadata.md) when clients depend on `prev`/`next`/`first`/`last` navigation links or precise page metadata semantics.
- Open [references/forwarded-headers-and-proxy-configuration.md](references/forwarded-headers-and-proxy-configuration.md) when generated links are wrong behind a reverse proxy, gateway, or base-path rewrite.
- Open [references/problem-details-error-representations.md](references/problem-details-error-representations.md) when error responses should use `application/problem+json` instead of ad hoc JSON payloads.
