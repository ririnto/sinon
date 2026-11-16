# Hateoas implementation examples

## Representation model and assembler

```java
class OrderModel extends RepresentationModel<OrderModel> {
    private final long id;
    private final String status;

    OrderModel(long id, String status) {
        this.id = id;
        this.status = status;
    }

    /**
     * Returns the order identifier exposed in the representation.
     */
    public long getId() {
        return id;
    }

    /**
     * Returns the order status exposed in the representation.
     */
    public String getStatus() {
        return status;
    }
}

@Component
class OrderModelAssembler implements RepresentationModelAssembler<Order, OrderModel> {
    /**
     * Builds the order representation with self and collection links.
     */
    @Override
    public OrderModel toModel(Order order) {
        OrderModel model = new OrderModel(order.id(), order.status());
        model.add(linkTo(methodOn(OrderController.class).one(order.id())).withSelfRel());
        model.add(linkTo(methodOn(OrderController.class).all()).withRel("orders"));
        return model;
    }

    /**
     * Builds the collection representation and adds the collection self link.
     */
    @Override
    public CollectionModel<OrderModel> toCollectionModel(Iterable<? extends Order> orders) {
        CollectionModel<OrderModel> models = RepresentationModelAssembler.super.toCollectionModel(orders);
        models.add(linkTo(methodOn(OrderController.class).all()).withSelfRel());
        return models;
    }
}
```

## Controller using the assembler

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

## Paged model assembly

```java
PagedModel<OrderModel> model = pagedResourcesAssembler.toModel(page, assembler);
```

## Affordance shape

```java
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;
import org.springframework.hateoas.mediatype.Affordances;
import org.springframework.http.HttpMethod;

Link self = linkTo(methodOn(OrderController.class).one(order.id())).withSelfRel();
Link update = Affordances.of(self)
    .afford(HttpMethod.PUT)
    .toLink();
```

Use affordances only when clients or generated UI flows will actually consume them.

## HalModelBuilder for embedded resources

```java
import org.springframework.hateoas.mediatype.hal.HalModelBuilder;

HalModelBuilder.halModelOf(order)
    .preview(new CustomerSummary(customer))
    .forLink(customerLink)
    .embed(additional)
    .link(Link.of("/orders/1", IanaLinkRelations.SELF))
    .build();
```

Use `HalModelBuilder` when the representation needs HAL `_embedded` entries with previews or nested documents.

## WebFlux links

```java
import reactor.core.publisher.Mono;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.reactive.WebFluxLinkBuilder;

Mono<Link> self = WebFluxLinkBuilder
    .linkTo(WebFluxLinkBuilder.methodOn(OrderController.class).one(order.id()))
    .withSelfRel()
    .toMono();
```

Use `WebFluxLinkBuilder.methodOn(...)` and keep the result as `Mono<Link>` in reactive applications.

## Hypermedia test shape

```java
@WebMvcTest(OrderController.class)
@Import(OrderModelAssembler.class)
class OrderRepresentationTests {
    @MockitoBean
    OrderService service;

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
