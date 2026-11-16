# Language Decisions

## Null safety first

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

## Validate contracts with `require`, `check`, `assert`

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

## Choose the smallest type shape

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

## Use extensions as local language tools

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

## Generics and inline reification

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

## Property delegation

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

## Keep member ordering predictable

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
