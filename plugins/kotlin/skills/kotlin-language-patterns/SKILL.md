---
name: kotlin-language-patterns
description: >-
  Use this skill when the user asks to "write idiomatic Kotlin", "refactor Java to Kotlin", "use Kotlin null safety", "choose data class vs sealed class", "use Kotlin serialization", "model Path or date-time values in Kotlin", "choose collection vs sequence", "use scope functions", "use runCatching or Result", "parse text with Regex", or needs guidance on Kotlin language and standard-library patterns.
---

## Goal

Write idiomatic Kotlin by choosing the smallest language construct or stdlib path that keeps meaning obvious.

**Minimum Kotlin version: 1.9** -- examples use `kotlin.time.Instant`, `value class` with `@JvmInline`, `kotlin.io.path.*`, and `fun interface`. Some APIs referenced (e.g., `kotlin.uuid`, `kotlin.io.encoding`) require Kotlin 2.0+ and are marked as experimental where applicable. Library versions (`kotlinx.serialization`, `kotlinx-datetime`) are managed through the project's dependency catalog; pin versions when adopting features from specific releases. Keep the common path focused on null safety, type modeling, extensions, collection shaping, string handling, boundary error flow, Java interop, and Kotlin-native boundary choices such as serialization, date-time, and JVM filesystem paths, then open a blocker reference only when deeper modeling or adjacent platform topics actually matter.

## Operating Rules

- MUST keep nullability explicit in public APIs.
- MUST NOT use `!!` in production code paths; validate at boundaries instead.
- MUST pin platform-type nullability at the Java interop boundary; never let `T!` propagate inward.
- SHOULD prefer `val` by default; use `var` only for backing fields, JavaBean compatibility, or circular construction dependencies.
- SHOULD choose the smallest type shape that matches the domain.
- SHOULD expose read-only collection interfaces from public APIs rather than mutable variants.
- SHOULD prefer direct string helpers before introducing `Regex`.
- SHOULD keep collection pipelines eager by default and move to `Sequence` only when laziness materially helps.
- SHOULD use `runCatching` and `Result` at parsing, I/O, or integration boundaries rather than ordinary local business flow.
- MUST preserve Java interoperability requirements when they matter.
- MUST call out JVM-only or experimental APIs inline instead of treating them as unconditional defaults.
- SHOULD keep class members in a stable scan order so the public shape stays predictable.

## Common-Path Procedure

1. Read the target type, function, or call site before changing syntax.
2. Decide whether the main issue is null safety, type shape, extension placement, collection shape, text parsing, boundary error handling, Java interop, or a Kotlin-native boundary such as serialization, date-time, or filesystem paths.
3. Start with the smallest Kotlin default: explicit nullable flow, direct collections, direct string helpers, and ordinary control flow.
4. Upgrade only when the contract clearly needs a stronger construct such as a `value class`, sealed hierarchy, `Sequence`, `Regex`, or `Result` boundary.
5. Open one blocker reference only when the remaining problem is deeper modeling ambiguity, null-and-scope-function cleanup, sequence tradeoffs, or an adjacent ecosystem boundary.

## Core Decisions

### Null safety first

Model absence directly and keep the flow readable.

```kotlin
fun primaryEmail(user: User?): String? =
    user?.emails?.firstOrNull { it.isPrimary }?.value
```

Use early returns, `?.`, `?:`, and `as?` before reaching for `!!`. When calling into Java code that returns a platform type (`T!`), pin nullability immediately at the interop edge:

```kotlin
val name: String = javaObject.getName()
val optional: String? = javaObject.getOptional()
```

Pin at the boundary -- never let platform types escape inward.

### Validate contracts with `require`, `check`, `assert`

Use `require` for argument validation (throws `IllegalArgumentException`), `check` for state validation (throws `IllegalStateException`), and `assert` for invariants that can be disabled in production:

```kotlin
fun connect(port: Int) {
    require(port in 1..65_535) { "Port must be in 1..65535, got $port" }
}

fun fetchData() {
    check(isConnected) { "Not connected" }
}

fun process(items: List<String>) {
    assert(items.distinct().size == items.size) { "Duplicates detected" }
}
```

`assert` calls are stripped when running without `-ea` JVM flag. Use `require` and `check` for validations that must always run; use `assert` for internal consistency checks that are safe to skip in production.

### Choose the smallest type shape

Use a `value class` for one wrapped domain value, a `data class` for immutable value carriers, a regular `class` for stateful behavior, an `object` for singleton behavior, and sealed modeling when the variant set is intentionally closed.

```kotlin
@JvmInline
value class CustomerId(val value: String)

data class Customer(val id: CustomerId, val name: String)

sealed interface PaymentResult {
    data class Approved(val authorizationId: String) : PaymentResult
    data class Rejected(val reason: String) : PaymentResult
}
```

Use `copy()` to create modified instances of a data class. Note that `copy()` performs a shallow copy -- nested mutable objects are shared between original and copy.

```kotlin
val updated = customer.copy(name = "Acme Corp")
```

Destructure data classes directly where the component names carry meaning:

```kotlin
data class GeoPoint(val lat: Double, val lng: Double)

fun formatLocation(point: GeoPoint): String {
    val (lat, lng) = point
    return "$lat,$lng"
}
```

Consume sealed types with exhaustive `when` expressions. The compiler enforces coverage of all subtypes:

```kotlin
fun describe(result: PaymentResult): String = when (result) {
    is PaymentResult.Approved -> "Auth: ${result.authorizationId}"
    is PaymentResult.Rejected  -> "Fail: ${result.reason}"
}
```

### Use extensions as local language tools

Use extensions when they make call sites clearer without hiding ownership or dispatch. Remember that members win over extensions and that extension dispatch is static.

```kotlin
fun String.normalizedIssueKey(): String = trim().uppercase()
```

Member dispatch is virtual; extension dispatch is static. The resolved implementation depends on the actual runtime type for members but on the declared compile-time type for extensions:

```kotlin
open class Base { fun greet() = "Base" }
class Derived : Base() { override fun greet() = "Derived" }

fun Base.greetExt() = "Base-ext"
fun Derived.greetExt() = "Derived-ext"

val b: Base = Derived()
b.greet()
b.greetExt()
```

Put polymorphic behavior in members; use extensions for utility surface that does not need runtime polymorphism.

Extension properties follow the same dispatch rules as extension functions -- static resolution on the declared type:

```kotlin
val String.isBlankOrEmpty: Boolean get() = isBlank() || isEmpty()

val List<Int>.median: Double?
    get() = if (isEmpty()) null else sorted()[size / 2].toDouble()
```

Use extension properties when the computed value reads as a natural attribute of the receiver type. Prefer extension functions when the operation involves parameters or performs side effects.

### Collections before `Sequence`

Prefer ordinary collections for finite in-memory work. Move to `Sequence` only when laziness or single-pass processing materially improves the path.

```kotlin
fun activeIds(customers: List<Customer>): List<CustomerId> =
    customers.filter { it.active }.map { it.id }
```

Expose read-only collection interfaces from public APIs so callers cannot mutate internal state:

```kotlin
class OrderRepository {
    private val _orders = mutableListOf<Order>()

    val orders: List<Order> get() = _orders
}
```

### Scope functions by intent

Use scope functions only when they make ownership or transformation clearer. Stop when nesting makes the path harder to scan than named locals.

| Function | Receiver available? | Return value | Typical use |
| --- | --- | --- | --- |
| `let` | `it` | Lambda result | Nullable handoff, transformations |
| `run` | `this` | Lambda result | Scoped computation, object init |
| `with` | `this` | Lambda result | Receiver-heavy code on existing object |
| `apply` | `this` | Receiver itself | Configuration, builder patterns |
| `also` | `it` | Receiver itself | Side-effects, logging, validation |

```kotlin
val email: String? = user?.let { it.emails.firstOrNull()?.value }

val request = HttpRequestBuilder().apply {
    method = HttpMethod.Get
    url = "https://api.example.com/users"
    header("Accept", "application/json")
}

val config = loadConfig().also { log.debug("Loaded config: $it") }

val result: Int = run {
    val a = computeA()
    val b = computeB()
    a + b
}

val formatted = with(json) {
    encodeToString(User.serializer(), user)
}
```

### Generics and inline reification

Use declaration-site variance to constrain how generic parameters flow through your API. Mark producers as `out T` and consumers as `in T`:

```kotlin
interface Source<out T> {
    fun next(): T?
}

interface Sink<in T> {
    fun accept(value: T)
}

val source: Source<String> = /* ... */
val ref: Source<Any> = source
```

Use `reified` type parameters in `inline` functions to access concrete type information at call sites. This enables `T::class`, `is` checks, and `as` casts without passing `Class<T>` explicitly:

```kotlin
inline fun <reified T> parseList(raw: String): List<T> =
    json.decodeFromString<List<T>>(raw)

val users: List<User> = parseList(rawJson)
```

Use `inline fun` sparingly. Inlining trades bytecode size for call-site performance and enables reification. Prefer regular functions unless you specifically need reified type parameters or have measured a hot-path bottleneck that inlining resolves.

Use `where` clauses when a generic type parameter has multiple upper bounds:

```kotlin
fun <T> serialize(value: T): String where T : Comparable<T>, T : Serializable {
    return "${value::class.simpleName}:${value}"
}
```

Star projections (`<*>`) let you accept a generic type without knowing its variance direction when you only read from it (equivalent to `out Any?`) or only write to it (equivalent to `in Nothing`):

```kotlin
fun printAll(items: List<*>) { items.forEach { println(it) } }
```

### Property delegation

Use `by lazy` for deferred initialization that runs once on first access:

```kotlin
class ConfigLoader {
    val config: AppConfig by lazy { loadFromDisk("app.conf") }
}
```

`lazy {}` defaults to `LazyThreadSafetyMode.SYNCHRONIZED` (double-checked locking). Use `LazyThreadSafetyMode.PUBLICATION` when the initialized value is safe to read before initialization completes and you want concurrent readers without synchronization overhead. Use `LazyThreadSafetyMode.NONE` only when the property is accessed from a single thread:

```kotlin
val config: AppConfig by lazy(LazyThreadSafetyMode.PUBLICATION) { loadFromDisk("app.conf") }
```

Use `Delegates.notNull` when a property must be assigned after construction but before any read:

```kotlin
var connection: DbConnection by Delegates.notNull()

fun init(dbUrl: String) {
    connection = openConnection(dbUrl)
}
```

Use `Delegates.observable` to track changes to a property automatically:

```kotlin
var retryCount: Int by Delegates.observable(0) { _, old, new ->
    log.info("retryCount changed: $old -> $new")
}
```

Use class delegation (`by`) to forward interface implementations to a backing instance without writing boilerplate forwarding methods:

```kotlin
class AuditedSet<E>(private val delegate: MutableSet<E> = mutableSetOf()) :
    MutableSet<E> by delegate {

    override fun add(element: E): Boolean {
        log.audit("add: $element")
        return delegate.add(element)
    }
}
```

### String helpers before `Regex`

Start with `trim`, `substringBefore`, `substringAfter`, `startsWith`, `split`, or `lineSequence`. Use `Regex` only when pattern matching is the real requirement.

Raw strings (`"""..."""`) preserve formatting and avoid escaping backslashes, which makes regex patterns and multi-line text readable:

```kotlin
private val issuePattern = Regex("""([A-Z]+)-(\d+)""")
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
class IssueKeyParser {
    private val issuePattern = Regex("""([A-Z]+)-(\d+)""")

    fun parse(input: String): Pair<String, Int>? {
        val trimmed = input.substringBefore('?').trim()
        val match = issuePattern.matchEntire(trimmed) ?: return null
        val (project, number) = match.destructured
        return project to number.toInt()
    }
}
```

### `Result` and `runCatching` at boundaries

Capture failures at parsing, I/O, or integration edges. Do not thread `Result` through every local branch of business logic.

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
    onSuccess = { port -> startServer(port) },
    onFailure = { ex -> log.error("Invalid port: ${ex.message}") }
)
```

Use `recover()` to transform specific failures into success values while letting others propagate:

```kotlin
parsePort(portStr).recover { ex ->
    if (ex is NumberFormatException) DEFAULT_PORT
    else throw ex // re-throw unexpected failures
}
```

Prefer `try/catch` over `Result` when you need different handling per exception type, `finally` blocks, or resource cleanup -- `Result` cannot distinguish exception classes natively and does not support `finally`.

### Keep Java callers in view

If Java calls the API, avoid surprising Kotlin-only assumptions around default parameters, nullability, and naming.

```kotlin
class OrderFormatter {
    @JvmOverloads
    fun format(orderId: String, uppercase: Boolean = false): String {
        return if (uppercase) orderId.uppercase() else orderId
    }
}
```

Expose companion-object members as static methods with `@JvmStatic` so Java callers do not need to reference the `Companion` holder:

```kotlin
class HttpClient {
    companion object {
        @JvmStatic
        fun create(): HttpClient = HttpClient()
    }
}
```

Expose properties as fields with `@JvmField` to avoid synthetic getter/setter generation for simple public properties:

```kotlin
class Constants {
    @JvmField
    val VERSION: String = "1.0.0"
}
```

Control the generated filename for top-level declarations with `@file:JvmName`:

```kotlin
@file:JvmName("KtStringUtils")

fun normalize(s: String): String = s.trim().lowercase()
```

Kotlin supports SAM (Single Abstract Method) conversion for Java interfaces, allowing lambda syntax where Java expects an anonymous class:

```kotlin
executor.execute(Runnable { println("running") })
executor.execute { println("running") }
```

Declare checked exceptions that Java callers must handle with `@Throws`:

```kotlin
@Throws(IOException::class)
fun readFile(path: String): String = File(path).readText()
```

Without `@Throws`, Java sees the method as `throws nothing` and cannot catch the exception with a checked-exception handler.

### Keep Kotlin-native boundaries explicit

Keep adjacent Kotlin-native boundaries in this skill even when their detailed implementation moves to references.

- use `kotlinx.serialization` when the boundary is Kotlin-first model encoding or decoding
- use `kotlin.time.Instant` for real moments in time, and keep `LocalDate`, `LocalDateTime`, and `TimeZone` in `kotlinx-datetime`
- use `java.nio.file.Path` plus `kotlin.io.path.*` on JVM when filesystem semantics matter more than raw strings

### Keep member ordering predictable

When one file defines a class with companion members, overrides, helper methods, and nested types, keep the ordinary scan order stable: static-like companion members first, then instance properties, constructors, companion methods, overridden methods, instance methods, and finally nested types.

```kotlin
class Example(private val value: String) {
    companion object {
        private const val TYPE = "example"

        fun of(value: String): Example = Example(value)
    }

    override fun toString(): String = value

    fun value(): String = value

    private class Parser
}
```

This follows the Kotlin coding-conventions expectation that class contents stay easy to scan instead of drifting into arbitrary order.

## First Safe Default

If the path is still unclear, start from one explicit nullable flow and one direct collection transformation.

```kotlin
@JvmInline
value class UserId(val value: Long)

data class User(val id: UserId, val active: Boolean)

fun activeUserIds(users: List<User>): List<UserId> =
    users.filter { it.active }.map { it.id }
```

## Validate the Result

Check these pass/fail conditions before you stop:

- nullability is explicit and `!!` is not acting as a design shortcut
- platform types (`T!`) are pinned at the Java interop boundary and never propagate inward
- `val` is preferred; every `var` has a documented reason (backing field, JavaBean, circular dependency)
- argument validation uses `require`, state validation uses `check`, internal invariants use `assert`
- public APIs expose read-only collection interfaces, not mutable variants
- the chosen type shape matches the domain meaning instead of syntax fashion
- data class `copy()` usage accounts for shallow-copy semantics
- sealed types are consumed with exhaustive `when` expressions
- extensions (functions and properties) improve the call site without hiding ownership rules; polymorphic behavior lives in members
- scope functions are readable in one pass with correct receiver/return semantics
- generics use declaration-site variance where appropriate; `where` clauses constrain multiple bounds; star projections hide unused variance
- inline reification is used sparingly and only when reified access or measured performance justifies it
- property delegation uses the right delegate for each job (`by lazy` with appropriate thread-safety mode, `Delegates.notNull`, `Delegates.observable`, `by`)
- collection code stays eager unless laziness materially helps
- direct string helpers were considered before `Regex`; raw strings and template expressions are used appropriately
- `Result` stays at boundaries rather than infecting ordinary business flow; `try/catch` is used when per-exception handling or resource cleanup is needed
- Java callers are not surprised by hidden Kotlin assumptions (`@JvmOverloads`, `@JvmStatic`, `@JvmField`, `@file:JvmName`, `@Throws`)
- serialization, date-time, and JVM path choices stay explicit instead of being silently pushed into unrelated plugins
- member ordering still leaves one file easy to scan from top to bottom

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| using `!!` as a design shortcut | null-safety turns into hidden runtime failure | model absence explicitly |
| using raw `String` or `Long` for meaningful IDs everywhere | domain meaning gets weaker | use a `value class` when one wrapped value has real semantic weight |
| converting every pipeline to `asSequence()` | laziness adds noise to small in-memory code | keep collections by default |
| using `Regex` for fixed delimiters or prefixes | parsing gets heavier than the real requirement | start with string helpers |
| nesting scope functions until the receiver becomes unclear | ownership and flow become hard to scan | use named locals or early returns |
| threading `Result` through ordinary business logic | local code becomes wrapper-heavy | keep `Result` at the boundary |
| assuming extension dispatch is virtual | members always win; extension resolution is static on declared type | put polymorphic behavior in members |
| using data class `copy()` expecting deep copy | `copy()` is shallow -- nested mutable objects are shared | use immutable nested types or deep clone explicitly |
| letting platform types (`T!`) propagate from Java interop | null safety guarantees dissolve inward | declare explicit nullability at the interop edge |
| relying on smart cast across lambda captures of `var` | compiler cannot prove the variable did not change between capture and use | capture the value in a local `val` before the lambda |

## Output Contract

Return:

1. the chosen Kotlin shape and why it fits the job
2. any nullability, collection, or parsing decisions that affect behavior
3. any Java-interop caveats that still matter
4. any blocker references needed for deeper branches

## Blocker References

Open only the reference that matches the remaining blocker.

| Open when... | Read... |
| --- | --- |
| choosing among `value class`, `data class`, regular `class`, `object`, enum, and sealed modeling still feels ambiguous | `./references/language-modeling.md` |
| cleaning up a null-heavy path or tangled scope-function chain is the real blocker | `./references/null-safety-and-scope-functions.md` |
| deciding whether laziness is worth the cost or restructuring a pipeline around `Sequence` is the blocker | `./references/collections-and-sequences.md` |
| implementing a JVM filesystem boundary needs exact `Path`, resource, or large-file handling code | `./references/path-filesystem.md` |
| modeling a timestamp, date-only concept, or civil time needs exact conversion guidance | `./references/datetime-modeling.md` |
| implementing Kotlin serialization needs exact `Json`, default-value, or contextual-serializer guidance | `./references/serialization-patterns.md` |
| deciding whether a stdlib surface is common, JVM-only, experimental, or outside the normal path needs explicit caveats | `./references/stdlib-boundaries.md` |

## Scope Boundaries

Use this skill for Kotlin language and stdlib common-path work: null safety, type shape, extensions, collections, scope functions, string handling, `Result` boundaries, generics and reification, property delegation, and Java interop basics.

It also owns Kotlin-native boundary choices for serialization, date-time modeling, JVM filesystem paths, and predictable member ordering when those questions are still Kotlin language or API-shape decisions.

Do not use this skill as the primary source for coroutine or Flow API design (use `kotlin-coroutines-flows` for that), Kotlin testing strategy (use `kotlin-test`), or runtime-specific diagnostics.
