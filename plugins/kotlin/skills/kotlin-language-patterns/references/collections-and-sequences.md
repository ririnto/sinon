---
description: >-
  Use for collection builders, compound rejection predicates, or pipeline laziness when the skill needs focused examples.
---

# Collections and Sequences

Use these examples when the Kotlin skill's collection rules need more detail.

## Lazy sequences

Use a lazy sequence when the source is large and the chain is selective:

```kotlin
fun loadEnabledUsers(lines: List<String>): List<UserId> =
    lines
        .asSequence()
        .map { line -> line.substringBefore('#') }
        .map(String::trim)
        .filterNot(String::isEmpty)
        .mapNotNull(String::toLongOrNull)
        .map(::UserId)
        .take(500)
        .toList()
```

## Compound rejection predicates

```kotlin
data class Subscription(val suspended: Boolean, val expired: Boolean)

fun activeSubscription(subscription: Subscription?): Subscription? =
    subscription?.takeUnless { current ->
        current.suspended && current.expired
    }

fun activeSubscriptionByNegation(subscription: Subscription?): Subscription? =
    subscription?.takeIf { current ->
        !(current.suspended && current.expired)
    }

fun activeSubscriptionBySplit(subscription: Subscription?): Subscription? =
    subscription
        ?.takeUnless { current -> current.suspended }
        ?.takeUnless { current -> current.expired }
```

The safe calls preserve a null result without evaluating either predicate when `subscription` is null.

For a non-null `subscription`, the compound predicate rejects only when both flags are true.
The negated `takeIf` version returns the same result as the compound `takeUnless` version.
The split chain rejects when either flag is true.

| Suspended | Expired | Compound result | Split result |
| --- | --- | --- | --- |
| `false` | `false` | `subscription` | `subscription` |
| `false` | `true` | `subscription` | `null` |
| `true` | `false` | `subscription` | `null` |
| `true` | `true` | `null` | `null` |

## Key Operations Reference

### Grouping and associating

```kotlin
val byCategory: Map<String, List<Order>> = orders.groupBy(Order::category)
val byId: Map<String, Order> = orders.associateBy(Order::id)
val lengths: Map<String, Int> = names.associateWith(String::length)
```

### Flattening and zipping

```kotlin
val allItems: List<Item> = orders.flatMap(Order::items)
val paired: List<Pair<String, Int>> = names.zip(ages)
val (namesAgain, agesAgain) = paired.unzip()
```

### Accumulation

```kotlin
val sum: Int = numbers.reduce { acc, n -> acc + n }
val joined: String = numbers.fold("") { acc, n -> "$acc,$n" }
```

### Building collections immutably

```kotlin
val filtered: List<String> = buildList {
    for (item in source) {
        if (item.isActive()) { add(item.name) }
    }
}

val uniqueNames: Set<String> = buildSet {
    for (item in source) {
        if (item.isActive()) { add(item.name) }
    }
}

val itemById: Map<String, Item> = buildMap {
    for (item in source) {
        put(item.id, item)
    }
}
```

### Batching and windowing

```kotlin
val batches: List<List<Order>> = orders.chunked(size = 100)
val triples: List<List<Int>> = numbers.windowed(size = 3)
val unique: List<User> = users.distinctBy(User::email)
```
