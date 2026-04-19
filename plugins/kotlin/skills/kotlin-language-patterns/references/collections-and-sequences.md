---
title: Collections and Sequences
description: >-
  Open this when deciding whether a Kotlin pipeline should stay eager or move to Sequence is the real blocker.
---

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
    users.filter { it.enabled }.map { it.name }
```

Lazy sequence when the source is large and the chain is selective:

```kotlin
fun loadEnabledUsers(lines: List<String>): List<UserId> {
    val cleaned = lines
        .asSequence()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { raw -> raw.toLongOrNull() }
        .map(::UserId)
        .take(500)

    return cleaned.toList()
}
```

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| converting every pipeline to `asSequence()` | lazy machinery adds noise without a payoff | stay eager until laziness helps materially |
| leaving a long pipeline unreadable because it is technically correct | the domain meaning gets buried | split the path into named steps |
| using `Sequence` and then immediately materializing after every step | the code pays complexity without keeping laziness | keep the pipeline either clearly lazy or clearly eager |
