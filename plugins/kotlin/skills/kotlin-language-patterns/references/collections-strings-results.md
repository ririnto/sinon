# Collections Strings Results

## Collections before `Sequence`

Prefer ordinary collections for finite in-memory work.
Move to `Sequence` only when laziness or single-pass processing materially improves the path.

```kotlin
fun activeIds(customers: List<Customer>): List<CustomerId> =
    customers.filter(Customer::active).map(Customer::id)
```

Expose read-only collection interfaces and return a snapshot when callers must not mutate internal state:

```kotlin
class OrderRepository {
    private val mutableOrders: MutableList<Order> = mutableListOf()

    val orders: List<Order> get() = mutableOrders.toList()
}
```

## Scope functions by intent

Use scope functions only when they make ownership or transformation clearer.
Stop when nesting makes the path harder to scan than named locals.

| Function | Receiver available? | Return value | Typical use |
| --- | --- | --- | --- |
| `let` | `it` | Lambda result | Nullable handoff, transformations |
| `run` | `this` | Lambda result | Scoped computation, object init |
| `with` | `this` | Lambda result | Receiver-heavy code on existing object |
| `apply` | `this` | Receiver itself | Configuration, builder patterns |
| `also` | `it` | Receiver itself | Side-effects, logging, validation |

```kotlin
val email: String? = user?.let { account -> account.emails.firstOrNull()?.value }

val request = HttpRequestBuilder().apply {
    method = HttpMethod.Get
    url = "https://api.example.com/users"
    header("Accept", "application/json")
}

val config = loadConfig().also { cfg -> log.debug("Loaded config: $cfg") }

val result: Int = run {
    computeA() + computeB()
}

val formatted = with(json) {
    encodeToString(User.serializer(), user)
}
```

## String helpers before `Regex`

Start with `trim`, `substringBefore`, `substringAfter`, `startsWith`, `split`, or `lineSequence`.
Use `Regex` only when pattern matching is the real requirement.

Raw strings (`"""..."""`) preserve formatting and avoid escaping backslashes, which makes regex patterns and multi-line text readable.
Use them for fixed JSON or regex expectations when exact string semantics matter.
Use raw strings with `trimIndent()` for multiline code text instead of escaped newline strings.
Preserve the intended indentation, newline data, and interpolation when changing string form.
Raw strings still interpolate `${}` expressions.
Write `${'$'}` when the content needs a literal dollar sign.
A trailing newline before the closing delimiter remains part of a multi-line value, so account for it in exact comparisons.

```kotlin
private val referencePattern: Regex = """([A-Z]+)-(\d+)""".toRegex()
```

Use `trimIndent()` to strip leading whitespace from multi-line raw strings, and `trimMargin()` when you want custom prefix-based stripping:

```kotlin
val query = """
    SELECT id, name
    FROM users
    WHERE active = true
""".trimIndent()

val template = """
    |Dear ${user.name},
    |
    |Your order #${order.id} has shipped.
""".trimMargin()
```

String templates support arbitrary expressions inside `${}`:

```kotlin
val greeting = "Hello, ${user.name.uppercase()}!"
val mathResult = "Sum: ${a + b}, Product: ${a * b}"
```

Combine `Regex` with string helpers to extract structured data:

```kotlin
class ReferenceKeyParser {
    private val referencePattern: Regex = """(?<project>[A-Z]+)-(?<number>\d+)""".toRegex()

    fun parse(input: String): Pair<String, Int>? =
        referencePattern
            .matchEntire(input.substringBefore('?').trim())
            ?.let { match ->
                requireNotNull(match.groups["project"]).value to
                    requireNotNull(match.groups["number"]).value.toInt()
            }
}
```

## `Result` and `runCatching` at boundaries

Capture failures at parsing, I/O, or integration edges.
Do not thread `Result` through every local branch of business logic.

```kotlin
fun parsePort(raw: String): Result<Int> =
    runCatching { raw.trim().toInt() }
        .mapCatching { port ->
            require(port in 1..65_535)
            port
        }
```

Use `fold()` to handle both success and failure branches in one expression:

```kotlin
parsePort(portStr).fold(
    onSuccess = ::startServer,
    onFailure = { ex -> log.error("Invalid port: ${ex.message}") }
)
```

Use `recover()` to transform specific failures into success values while letting others propagate:

```kotlin
parsePort(portStr).recover { ex ->
    when (ex) {
        is NumberFormatException -> DEFAULT_PORT
        else -> throw ex
    }
}
```

Unexpected failures should be re-thrown so `recover` does not hide unrelated errors.

Prefer `try/catch` over `Result` when you need different handling per exception type, `finally` blocks, or resource cleanup -- `Result` cannot distinguish exception classes natively and does not support `finally`.
