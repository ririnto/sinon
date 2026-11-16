# Configuration and test examples

## Example 1: Controller-method links

```java
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

Link self = linkTo(methodOn(OrderController.class).one(order.id())).withSelfRel();
Link orders = linkTo(methodOn(OrderController.class).all()).withRel("orders");
```

## Example 2: Link relations

```java
import org.springframework.hateoas.IanaLinkRelations;

model.add(linkTo(methodOn(OrderController.class).one(order.id())).withSelfRel());
model.add(Link.of("/docs/orders", IanaLinkRelations.PROFILE));
model.add(Link.of("/admin/orders", LinkRelation.of("manage")));
```
