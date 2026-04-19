---
title: Null Safety and Scope Functions
description: >-
  Open this when a null-heavy path or tangled scope-function chain is the real blocker.
---

Open this when nullable flow and scope-function readability are the hard part.

## Rules

- prefer nullable types plus explicit handling over `!!`
- use early returns when absence should stop the current path
- use `let` for nullable handoff, `run` for scoped computation, `apply` for receiver configuration, and `also` for side-effect steps
- stop nesting scope functions when the receiver or return value stops being obvious
- prefer a named local when it makes ownership or intermediate meaning clearer

## Patterns

Nullable handoff with an early return:

```kotlin
fun primaryEmail(user: User?): String? {
    val account = user ?: return null
    return account.emails.firstOrNull { it.isPrimary }?.value
}
```

`let` only when it clarifies the next step:

```kotlin
fun displayName(user: User?): String =
    user?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "anonymous"
```

`apply` for local configuration:

```kotlin
val request = HttpRequest().apply {
    method = "POST"
    path = "/orders"
}
```

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| chaining nullable scope functions until the receiver becomes unclear | readers must mentally reconstruct the object flow | use a named local or early return |
| using `!!` to avoid making absence explicit | failure moves to runtime | keep the API nullable or validate at the boundary |
| using `also` or `apply` when the return value matters more than the receiver | the chosen scope function hides intent | pick the scope function by intent, not habit |
