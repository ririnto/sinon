# Spring Data scrolling patterns

Open this reference when the blocker is scroll position semantics, `WindowIterator`, or why a projection fails under keyset scrolling.

## Offset versus keyset blocker

Use offset scrolling when the sort columns can be null or when the caller cannot keep a stable keyset position. Use keyset scrolling when the result set is large and the query can sort on stable, non-null columns.

```java
Window<Customer> firstWindow = repository.findFirst20ByAddressCityOrderByIdAsc("Seoul", ScrollPosition.offset());

Window<Customer> nextWindow = repository.findFirst20ByAddressCityOrderByIdAsc("Seoul", firstWindow.positionAt(firstWindow.size() - 1));
```

`ScrollPosition.offset()` means 'start of scroll'. Carry forward the returned position from the previous `Window`; do not invent one manually.

## WindowIterator blocker

Use `WindowIterator` when callers need to consume the whole scroll without hand-writing the loop.

```java
WindowIterator<Customer> iterator = WindowIterator.of(position ->
    repository.findFirst20ByAddressCityOrderByIdAsc("Seoul", position)
).startingAt(ScrollPosition.offset());

while (iterator.hasNext()) {
    Customer customer = iterator.next();
}
```

## Projection blocker

Keyset scrolling requires the projection to include the sort properties that anchor the scroll position.

```java
record CustomerScrollView(Long id, String city, String email) {
}

Window<CustomerScrollView> findFirst20ByAddressCityOrderByIdAsc(String city, KeysetScrollPosition position);
```

If the query sorts by `id`, keep `id` in the projection. Omitting a sort property breaks keyset extraction.

Use `KeysetScrollPosition` in the repository signature and start with `ScrollPosition.keyset()` when the repository method is explicitly designed for keyset scrolling and the sort columns satisfy the non-null constraint.

## Decision points

| Situation | First check |
| --- | --- |
| large result set and stable sort columns | choose keyset scrolling |
| nullable sort property | fall back to offset scrolling |
| caller keeps writing manual window loops | move to `WindowIterator` |
| projection fails under scrolling | verify every sort property is present in the projection |
