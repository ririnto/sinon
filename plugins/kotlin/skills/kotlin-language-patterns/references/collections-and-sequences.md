---
description: >-
  Open this when deciding whether a Kotlin pipeline should stay eager or move to Sequence is the real blocker.
---

# Collections and Sequences

Open this when collection shape and laziness tradeoffs are the hard part.

## Rules

- keep ordinary collection pipelines as the default for finite in-memory work
- switch to `Sequence` only when laziness or single-pass processing materially improves the path
- break long chains into named locals when business meaning is getting hidden
- prefer simple `map`, `filter`, `associate`, and `groupBy` before clever pipeline tricks

## Patterns

Readable eager pipeline:

```kotlin
fun enabledNames(users: List<User>): List<String> =
    users.filter(User::enabled).map(User::name)
```

Lazy sequence when the source is large and the chain is selective:

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

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| converting every pipeline to `asSequence()` | lazy machinery adds noise without a payoff | stay eager until laziness helps materially |
| leaving a long pipeline unreadable because it is technically correct | the domain meaning gets buried | split the path into named steps |
| using `Sequence` and then immediately materializing after every step | the code pays complexity without keeping laziness | keep the pipeline either clearly lazy or clearly eager |

## Key Operations Reference

### Grouping and associating

```kotlin
val byCategory: Map<String, List<Order>> = orders.groupBy(Order::category)
val byId: Map<String, Order> = orders.associateBy(Order::id)
val lengths: Map<String, Int> = names.associateWith(String::length)
val pairs: Map<String, Int> = names.associateWith { name -> name to name.length }
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
```

### Batching and windowing

```kotlin
val batches: List<List<Order>> = orders.chunked(size = 100)
val triples: List<List<Int>> = numbers.windowed(size = 3)
val unique: List<User> = users.distinctBy(User::email)
```
