# Spring Data AOT and native-image

Open this reference when the ordinary repository-abstraction path in `SKILL.md` is not enough and the task depends on AOT or native-image repository details.

Use this branch only when the deployment target requires those constraints.

## What AOT changes

Spring Data AOT precomputes repository metadata and type access paths so native-image or AOT builds do not rely on broad runtime reflection.

Use this branch only when the application actually builds with AOT or native-image constraints.

## Repository eligibility rule

AOT support works best with repository methods that stay within supported query and projection patterns.

Good fit:

- derived queries
- declared `@Query` methods
- projections with stable return shapes
- pageable and sortable repository methods

Riskier fit:

- dynamic reflection-heavy patterns
- open-ended runtime proxy behavior
- store-specific features not covered by the target module's AOT support

## Configuration shape

```properties
spring.aot.enabled=true
```

Keep the build and runtime target explicit so repository code is validated under the same AOT assumptions.

## Entity or document discovery rule

AOT builds benefit from explicit domain annotations and predictable repository scanning.

```java
@Entity
class Order {
}
```

```java
@Document
class OrderDocument {
}
```

## Decision points

| Situation | Guidance |
| --- | --- |
| JVM deployment only | stay on ordinary repository path |
| native-image target is required | review repository patterns for AOT compatibility |
| repository behavior depends on dynamic runtime tricks | expect additional AOT work or avoid that pattern |
