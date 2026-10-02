---
metadata:
  reference:
    Kotlin:
      version: 2.4.20
      url: https://github.com/JetBrains/kotlin/releases/tag/v2.4.20
    Kotlin EAP documentation:
      url: https://kotlinlang.org/docs/eap.html
    Kotlin release notes:
      - version: 2.2.0
        url: https://kotlinlang.org/docs/whatsnew22.html
      - version: 2.3.0
        url: https://kotlinlang.org/docs/whatsnew23.html
      - version: 2.4.0
        url: https://kotlinlang.org/docs/whatsnew24.html
    Kotlin standard library API:
      url:
        - https://kotlinlang.org/api/core/kotlin-stdlib/
        - https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/require-not-null.html
        - https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/check-not-null.html
        - https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/to-regex.html
    Kotlin scope functions:
      url:
        - https://kotlinlang.org/docs/scope-functions.html
        - https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/take-if.html
    Kotlin JVM path extensions:
      url: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/
    Kotlin Path composition:
      version: Kotlin 1.5+
      url: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/div.html
    kotlinx-datetime:
      version: 0.8.0
      url: https://github.com/Kotlin/kotlinx-datetime/blob/v0.8.0/README.md
    kotlinx.serialization Instant support:
      version: 1.9.0
      url: https://github.com/Kotlin/kotlinx.serialization/blob/v1.9.0/CHANGELOG.md
    kotlinx.serialization Instant component serializer:
      version: 1.11.0
      url: https://github.com/Kotlin/kotlinx.serialization/blob/v1.11.0/core/commonMain/src/kotlinx/serialization/builtins/InstantComponentSerializer.kt
    Java Path API:
      version: Java SE 25
      url: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Path.html
name: kotlin-language-patterns
description: >-
  Design or refactor Kotlin types, null handling, collections, extensions, Java interop, or stdlib boundaries.
---

# Kotlin Language Patterns

## Goal

Write idiomatic Kotlin by choosing the smallest language construct or stdlib path that keeps meaning obvious.

Minimum Kotlin version: 2.1 -- examples use `value class` with `@JvmInline`, `kotlin.io.path.*`, and `fun interface`.
The referenced Kotlin 2.4.20 release is an example baseline.
For a compiler upgrade, check the latest stable Kotlin release and confirm library compatibility before updating the project catalog.
Exclude EAP builds from the stable-release comparison.
Newer stdlib surfaces are version-gated and noted where they appear: `kotlin.io.encoding` is stable since Kotlin 2.2, `kotlin.time.Instant` is stable since Kotlin 2.3, and `kotlin.uuid` is stable since Kotlin 2.4.
On the 2.1 baseline `kotlinx.datetime.Instant` remains the portable choice for a real moment in time.
That older API requires an existing compatible `0.6.x` library or the documented `0.7.x` compatibility release.
Current `kotlinx-datetime` uses `kotlin.time.Instant` and `kotlin.time.Clock` instead.
The date-time and serialization examples use these stable types and require Kotlin 2.3 or later.
Use the project's managed `kotlinx.serialization` and `kotlinx-datetime` versions when they support the required APIs.
For new dependencies or required upgrades, check Maven Central for the latest stable Kotlin-compatible version and record it in the project catalog.

## Operating Rules

- MUST keep nullability explicit in public APIs.
- MUST NOT use `!!` in production code paths.
  - Validate at boundaries instead.
- MUST pin platform-type nullability at the Java interop boundary.
  - Never let `T!` propagate inward.
- SHOULD prefer `val` by default.
  - `val` prevents reassignment but does not make the referenced object or collection immutable.
  - Use `var` only for backing fields, JavaBean compatibility, or circular construction dependencies.
- SHOULD preserve evaluation order, evaluation count, exception timing, mutable snapshots, and closure capture when changing a binding to `val`.
- SHOULD choose the smallest type shape that matches the domain.
- SHOULD use `?.let` when nullable data should trigger work only when present.
  Use `?:` for an intentional default or required-value failure.
- SHOULD use `requireNotNull` for required arguments and `checkNotNull` for required state instead of Boolean null preconditions.
  Preserve exception types, messages, evaluation order, wrapper contracts, and `Unit` return types.
- SHOULD invert a guard condition to keep the main path positive instead of using `return`, `break`, or `continue`.
  Do this only when behavior stays the same and no nesting or mutable state is added.
- SHOULD use `when (subject)` when one value determines the branches.
- SHOULD split `filter` predicates with `&&` into chained filters when their semantics stay unchanged.
  Preserve evaluation order, nullability, smart casts, side effects, and eager or lazy behavior.
  Keep one predicate when splitting changes any of those semantics.
- SHOULD chain independent positive `takeIf` predicates instead of combining them with `&&`.
  Use a safe call before each `takeIf` when the receiver is nullable.
  Keep the original order and short-circuit behavior.
  Keep `takeUnless` and `filterNot` predicates intact.
  Do not split mixed positive and negative predicates when the Boolean logic changes.
- SHOULD use callable references for simple `map`, `filter`, and similar lambdas when overload and receiver resolution stay unchanged.
  Keep a lambda when a reference changes evaluation or meaning.
- MUST declare an explicit type for every class, object, and companion object property, including private properties.
- MUST declare an explicit type for private top-level properties.
- SHOULD prefer `kotlin.io.path.div` for JVM `Path` composition when the Kotlin standard library is available.
- SHOULD expose read-only collection interfaces from public APIs rather than mutable variants.
- SHOULD prefer direct string helpers before introducing `Regex`.
- SHOULD use `String.toRegex()` when a regular expression is required.
- SHOULD keep short calls on one line when they fit the configured line limit.
- SHOULD pass an existing function reference instead of wrapping it in a lambda when the meaning, receiver binding, and overload resolution stay identical.
- SHOULD document every effectively public declaration with KDoc.
  - Consider the enclosing visibility: a public member inside an `internal` or `private` holder is not public API and does not require KDoc.
  - Private helpers and local callbacks stay undocumented unless the contract is surprising.
- SHOULD prefer `tailrec` over a loop when the recursive call is in real tail position and semantics and readability hold.
- SHOULD keep collection pipelines eager by default and move to `Sequence` only when laziness materially helps.
- SHOULD use `runCatching` and `Result` at parsing, I/O, or integration boundaries rather than ordinary local business flow.
- MUST preserve Java interoperability requirements when they matter.
- MUST call out JVM-only or experimental APIs inline instead of treating them as unconditional defaults.
- MUST NOT use the implicit `it` lambda parameter.
  Name the parameter with a meaningful role or use a callable reference.
  - This rule never expires for short scopes.
  - The `it` name of a Kotest test-case DSL call, such as `it("calculates total") { }`, is a test name argument, not an implicit lambda parameter.
- SHOULD use infix functions only when the operation reads naturally at the call site and stays unambiguous without extra context.
- SHOULD keep class members in a stable scan order so the public shape stays predictable.
- MUST declare constructor parameters that a container or DI framework supplies non-null and default-free.
  - A missing required value fails registration instead of falling back to a code default or a nullable property.
  - This rule targets registered classes only.
    Domain and protocol values keep explicit nullable flow.
- SHOULD express optional behavior as explicit strategy implementations selected at composition time instead of a nullable or defaulted dependency.

## Task Context

Read the target declaration and relevant callers before changing its syntax or contract.
Choose the smallest language construct that preserves behavior and the repository's Kotlin baseline.
Use the reference table below for the modeling, collection, or platform boundary under change.

## References

Read the references that match the current decision.

| Open when... | Read... |
| --- | --- |
| choosing among `value class`, `data class`, regular `class`, `object`, enum, and sealed modeling still feels ambiguous | `./references/language-modeling.md` |
| cleaning up a null-heavy path or tangled scope-function chain is the real blocker | `./references/null-safety-and-scope-functions.md` |
| deciding whether laziness is worth the cost or restructuring a pipeline around `Sequence` is the blocker | `./references/collections-and-sequences.md` |
| implementing a JVM filesystem boundary needs exact `Path`, resource, or large-file handling code | `./references/path-filesystem.md` |
| modeling a timestamp, date-only concept, or civil time needs exact conversion guidance | `./references/datetime-modeling.md` |
| implementing Kotlin serialization needs exact `Json`, default-value, or contextual-serializer guidance | `./references/serialization-patterns.md` |
| deciding whether a stdlib surface is common, JVM-only, experimental, or outside the normal path needs explicit caveats | `./references/stdlib-boundaries.md` |

## Core Decisions

### Null safety first

Model absence directly and keep the flow readable.

```kotlin
fun primaryEmail(user: User?): String? =
    user?.emails?.firstOrNull(Email::isPrimary)?.value
```

For a nullable receiver with independent positive criteria, use a safe-call step for each predicate:

```kotlin
data class AccountPolicy(val enabled: Boolean, val verified: Boolean)

fun eligibleAccount(account: AccountPolicy?): AccountPolicy? =
    account
        ?.takeIf(AccountPolicy::enabled)
        ?.takeIf(AccountPolicy::verified)
```

Use `?.let` when nullable data should trigger work only when present.
Use `?:` for an intentional default or required-value failure.
Use `as?` for a safe cast.
Invert a guard condition to keep the main path positive instead of using `return`, `break`, or `continue`.
Do this only when behavior stays the same and no nesting or mutable state is added.
Use `when (subject)` when one value determines the branches.
When calling into Java code that returns a platform type (`T!`), pin nullability immediately at the interop edge:

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

On the JVM, `assert` checks run only when assertions are enabled with `-ea`.
The compiler does not remove the calls.
Use `require` and `check` for validations that must always run.
Use `assert` for internal consistency checks that are safe to skip in production.

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

Use `copy()` to create modified instances of a data class.
Note that `copy()` performs a shallow copy -- nested mutable objects are shared between original and copy.

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

Consume sealed types with exhaustive `when` expressions.
The compiler enforces coverage of all subtypes:

```kotlin
fun describe(result: PaymentResult): String = when (result) {
    is PaymentResult.Approved -> "Auth: ${result.authorizationId}"
    is PaymentResult.Rejected  -> "Fail: ${result.reason}"
}
```

### Use extensions as local language tools

Use extensions when they make call sites clearer without hiding ownership or dispatch.
Remember that members win over extensions and that extension dispatch is static.

```kotlin
fun String.normalizedReferenceKey(): String = trim().uppercase()
```

Member dispatch is virtual.
Extension dispatch is static.
The resolved implementation depends on the actual runtime type for members but on the declared compile-time type for extensions:

```kotlin
open class Base { open fun greet(): String = "Base" }
class Derived : Base() { override fun greet(): String = "Derived" }

fun Base.greetExt(): String = "Base-ext"
fun Derived.greetExt(): String = "Derived-ext"

val b: Base = Derived()
b.greet()
b.greetExt()
```

Put polymorphic behavior in members.
Use extensions for utility surface that does not need runtime polymorphism.

Extension properties follow the same dispatch rules as extension functions -- static resolution on the declared type:

```kotlin
val GeoPoint.isNorthernHemisphere: Boolean
    get() = lat >= 0.0
```

Use extension properties when the computed value reads as a natural attribute of the receiver type and each access costs no more than the equivalent call would.
Prefer extension functions when the operation involves parameters or performs side effects.
Do not hide expensive work, mutation, or surprising derived state behind field-like property syntax.

### Collections before `Sequence`

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

### Scope functions by intent

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

### Generics and inline reification

Use declaration-site variance to constrain how generic parameters flow through your API.
Mark producers as `out T` and consumers as `in T`:

```kotlin
interface Source<out T> {
    fun next(): T?
}

interface Sink<in T> {
    fun accept(value: T)
}

val source: Source<String> = object : Source<String> {
    override fun next(): String? = "value"
}
val ref: Source<Any> = source
```

Use `reified` type parameters in `inline` functions to access concrete type information at call sites.
This enables `T::class`, `is` checks, and `as` casts without passing `Class<T>` explicitly:

```kotlin
inline fun <reified T> parseList(raw: String): List<T> =
    json.decodeFromString<List<T>>(raw)

val users: List<User> = parseList(rawJson)
```

Inlining trades bytecode size for call-site performance and enables reification.
Prefer regular functions unless you specifically need reified type parameters or have measured a hot-path bottleneck that inlining resolves.

Use `where` clauses when a generic type parameter has multiple upper bounds:

```kotlin
fun <T> serialize(value: T): String where T : Comparable<T>, T : Serializable {
    return "${value::class.simpleName}:${value}"
}
```

Star projections (`<*>`) let you accept a generic type without knowing its variance direction when you only read from it (equivalent to `out Any?`) or only write to it (equivalent to `in Nothing`):

```kotlin
fun printAll(items: List<*>) { items.forEach(::println) }
```

### Property delegation

Use `by lazy` for deferred initialization that runs once on first access:

```kotlin
class ConfigLoader {
    val config: AppConfig by lazy { loadFromDisk("app.conf") }
}
```

`lazy {}` defaults to `LazyThreadSafetyMode.SYNCHRONIZED` (double-checked locking).
Use `LazyThreadSafetyMode.PUBLICATION` when concurrent initializer calls are safe.
Only one completed value is published to all readers.
Use `LazyThreadSafetyMode.NONE` only when the property is accessed from a single thread:

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
    private val referencePattern: Regex = """([A-Z]+)-(\d+)""".toRegex()

    fun parse(input: String): Pair<String, Int>? =
        referencePattern
            .matchEntire(input.substringBefore('?').trim())
            ?.destructured
            ?.let { (project, number) -> project to number.toInt() }
}
```

### `Result` and `runCatching` at boundaries

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

### Keep Java callers in view

If Java calls the API, avoid surprising Kotlin-only assumptions around default parameters, nullability, and naming.

```kotlin
class OrderFormatter {
    @JvmOverloads
    fun format(orderId: String, uppercase: Boolean = false): String = when (uppercase) {
        true -> orderId.uppercase()
        false -> orderId
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
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.readText

@Throws(IOException::class)
fun readFile(path: Path): String = path.readText()
```

Without `@Throws`, Java sees the method as `throws nothing` and cannot catch the exception with a checked-exception handler.

### Keep Kotlin-native boundaries explicit

Keep adjacent Kotlin-native boundaries in this skill even when their detailed implementation moves to references.

- use `kotlinx.serialization` when the boundary is Kotlin-first model encoding or decoding
- use `kotlinx.datetime.Instant` for real moments in time on the Kotlin 2.1 baseline (stdlib `kotlin.time.Instant` is stable since Kotlin 2.3), and keep `LocalDate`, `LocalDateTime`, and `TimeZone` in `kotlinx-datetime`
- use the JVM `Path` type with Kotlin `kotlin.io.path.*` extensions for common filesystem operations

### Keep member ordering predictable

When one file defines a class with companion members, overrides, helper methods, and nested types, keep the ordinary scan order stable: static-like companion members first, then instance properties, constructors, companion methods, overridden methods, instance methods, and finally nested types.

```kotlin
class Example(private val value: String) {
    companion object {
        private const val TYPE: String = "example"

        fun of(value: String): Example = Example(value)
    }

    override fun toString(): String = value

    fun value(): String = value

    private class Parser
}
```

This follows the Kotlin coding-conventions expectation that class contents stay easy to scan instead of drifting into arbitrary order.

## Completion

Explain the selected Kotlin shape and any material nullability, collection, parsing, or Java-interop consequence.
For source edits, preserve the operating rules and verify affected behavior with the repository's native checks.
Do not review unrelated language features to complete a checklist.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| using `!!` as a design shortcut | null-safety turns into hidden runtime failure | model absence explicitly |
| using raw `String` or `Long` for meaningful IDs everywhere | domain meaning gets weaker | use a `value class` when one wrapped value has real semantic weight |
| converting every pipeline to `asSequence()` | laziness adds noise to small in-memory code | keep collections by default |
| using `Regex` for fixed delimiters or prefixes | parsing gets heavier than the real requirement | start with string helpers |
| nesting scope functions until the receiver becomes unclear | ownership and flow become hard to scan | use named locals or `?.let` at the nullable boundary |
| threading `Result` through ordinary business logic | local code becomes wrapper-heavy | keep `Result` at the boundary |
| assuming extension dispatch is virtual | members always win because extension resolution is static on the declared type | put polymorphic behavior in members |
| using data class `copy()` expecting deep copy | `copy()` is shallow -- nested mutable objects are shared | use immutable nested types or deep clone explicitly |
| letting platform types (`T!`) propagate from Java interop | null safety guarantees dissolve inward | declare explicit nullability at the interop edge |
| relying on smart cast across lambda captures of `var` | compiler cannot prove the variable did not change between capture and use | capture the value in a local `val` before the lambda |

## Scope Boundaries

Coroutine or Flow API design, Kotlin testing strategy, and runtime-specific diagnostics are adjacent domains outside this language-and-stdlib scope.
