---
description: >-
  Open this when a null-heavy path or tangled scope-function chain is the real blocker.
---

# Null Safety and Scope Functions

Open this when nullable flow and scope-function readability are the hard part.

## Rules

- prefer nullable types plus explicit handling over `!!`
- use `?.let` when nullable data should trigger work only when present
- use `?:` for an intentional default or a required-value failure
- invert a guard condition to keep the main path positive instead of using `return`, `break`, or `continue`
- apply that rewrite only when behavior stays the same and no nesting or mutable state is added
- use `when (subject)` when one value determines the branches
- chain independent positive `takeIf` predicates, and use `?.takeIf` at each nullable step
- keep predicate order and short-circuit behavior when splitting a condition
- use `let` for nullable handoff, `run` for scoped computation, `apply` for receiver configuration, and `also` for side-effect steps
- stop nesting scope functions when the receiver or return value stops being obvious
- prefer a named local when it makes ownership or intermediate meaning clearer

## Patterns

Nullable handoff with `?.`:

```kotlin
fun primaryEmail(user: User?): String? =
    user?.emails?.firstOrNull(Email::isPrimary)?.value
```

The same handoff when absence must stop the current path:

```kotlin
fun requiredPrimaryEmail(user: User?): String =
    user?.let { account ->
        account.emails.firstOrNull(Email::isPrimary)?.value
            ?: throw IllegalArgumentException("user has no primary email")
    } ?: throw IllegalArgumentException("user must not be null")
```

Use one safe-call step for each independent positive condition on a nullable receiver:

```kotlin
data class AccountPolicy(val enabled: Boolean, val verified: Boolean)

fun eligibleAccount(account: AccountPolicy?): AccountPolicy? =
    account
        ?.takeIf(AccountPolicy::enabled)
        ?.takeIf(AccountPolicy::verified)
```

`let` only when it clarifies the next step:

```kotlin
fun displayName(user: User?): String =
    user?.name?.trim()?.takeIf(String::isNotEmpty) ?: "anonymous"
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
| chaining nullable scope functions until the receiver becomes unclear | readers lose track of the object flow | use a named local or one clear `?.let` boundary |
| using `!!` to avoid making absence explicit | failure moves to runtime | keep the API nullable or validate at the boundary |
| using `also` or `apply` when the return value matters more than the receiver | the chosen scope function hides intent | pick the scope function by intent, not habit |

## Platform Types

Types coming from Java without explicit nullability (`String!`) are platform types.
Rule: never let platform types propagate inward.
Pin the nullability at the interop boundary and never let a raw `T!` escape into application logic.

## Late Initialization

Use `lateinit var` for non-primitive properties that the constructor cannot set and a non-DI initialization lifecycle fills before first access:

```kotlin
class Service {
    lateinit var repository: Repository

    fun init(repo: Repository) {
        repository = repo
    }

    fun isReady(): Boolean = ::repository.isInitialized
}
```

Restrictions: `lateinit` only works with non-primitive types that do not have a custom getter.
Access before initialization throws `UninitializedPropertyAccessException`.
A class that a container or DI framework constructs takes its dependencies through non-null, default-free constructor parameters instead of a `lateinit` property.

## Smart Cast Limits

Smart casts apply automatically in most cases but fail in these situations:

```kotlin
class Container(val item: Any?)

fun printLength(c: Container) {
    val value = c.item
    when (value) {
        is String -> println(value.length)
        else -> Unit
    }
}

fun process(varValue: String?) {
    varValue?.let { value -> println(value.length) }
}
```

Smart casts fail when a custom getter prevents the compiler from tracking the type.
A nullable value captured in a lambda is handled with `?.let` and a named non-null parameter instead of a temporary `val` and guard return.
