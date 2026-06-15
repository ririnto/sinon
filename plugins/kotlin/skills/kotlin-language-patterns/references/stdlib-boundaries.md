---
description: >-
  Open this when platform-specific or experimental stdlib APIs need explicit caveats.
---

# Platform and Experimental Stdlib Boundaries

Use this reference when the job is to recommend a Kotlin stdlib API responsibly, with the right platform and stability caveats.
This reference should be sufficient on its own for that decision.

Use this file to finish one of these jobs:

- decide whether an API is common, JVM-only, or platform-filtered
- decide whether an API is experimental enough to require an inline warning
- keep less-common stdlib surfaces from being presented as ordinary defaults
- explain why a JVM example should not be copied into multiplatform code unchanged

Best-practice rules:

- treat the Kotlin stdlib docs as versioned and platform-filtered.
  - Examples should read as `Common` first unless the code is intentionally runtime-specific
- `kotlin.io.path.*` is JVM-only and some APIs are marked `ExperimentalPathApi`.
  - Use it only when the module is explicitly on JVM and real filesystem `Path` behavior matters
- `kotlin.io.encoding` is stable since Kotlin 2.2. Use it when encoding support is required, and remember that the stream helpers there are JVM-only
- stdlib `kotlin.time.Instant` is stable since Kotlin 2.3. On the Kotlin 2.1 baseline use `kotlinx.datetime.Instant`, or raise the baseline to 2.3+ for the stdlib type
- `kotlin.uuid` is stable since Kotlin 2.4 (experimental since 2.0).
  - Use it when UUID generation or parsing is genuinely needed
- `kotlin.contracts` is experimental and is not a common-path recommendation for ordinary application code
- `Regex` exists across platforms, but options and behavior can differ because JS uses the host `RegExp` behavior with stricter Unicode parsing
- examples that use `java.io.File`, `BufferedReader`, or other JDK resource types are JVM-specific illustrations even when the surrounding stdlib concept is broader
- stdlib `kotlin.coroutines` is the low-level coroutine surface.
  - Higher-level async work still belongs in `kotlinx.coroutines` guidance rather than this language-pattern entrypoint

Prefer examples that make runtime-specific or experimental status explicit instead of leaving it implicit.

JVM-only helper with explicit boundary:

```kotlin
import java.nio.file.Path
import kotlin.io.path.readText

/** JVM-only: uses kotlin.io.path on top of java.nio.file.Path. */
fun loadJvmConfig(path: Path): String = path.readText()
```

Use when: the code is intentionally JVM-specific and the example should say so directly.

Cross-platform `Regex` with portability caveat:

```kotlin
/** Cross-platform Regex, but option and Unicode behavior can differ on JS. */
private val orderPattern = Regex("""\w+-\d+""")
```

Use when: the example is multiplatform in principle, but callers should not assume every engine behaves identically.

Recently stabilized API with explicit status:

State an API's current stability in prose before imports or on a declaration KDoc, not as a detached KDoc before an import.
`kotlin.uuid` graduated to Stable in Kotlin 2.4, so it no longer requires an opt-in.

```kotlin
import kotlin.uuid.Uuid

/** Generates a UUID; stable since Kotlin 2.4, no opt-in required. */
fun createId(): Uuid = Uuid.random()
```

Use when: an API recently graduated from experimental and callers should know it is now a stable, default recommendation rather than an opt-in surface.

Experimental contracts with explicit non-default framing:

The opt-in caveat should appear in prose before the snippet and on the declaration that owns the behavior.

```kotlin
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/** Requires a non-null name with contracts; use only when simpler checks are not enough. */
@OptIn(ExperimentalContracts::class)
fun requireNotNullName(name: String?) {
    contract { returns() implies (name != null) }
    require(name != null)
}
```

Use when: the team is intentionally opting into contracts and the example must make that non-default status obvious.

Encoding helper with explicit platform status:

`kotlin.io.encoding.Base64` is stable since Kotlin 2.2. The stream helpers are JVM-only.

```kotlin
import kotlin.io.encoding.Base64

/** Encodes bytes with the stdlib Base64 API (stable since 2.2); stream helpers are JVM-only. */
fun encode(raw: ByteArray): String = Base64.encode(raw)
```

Use when: the example needs encoding support and callers must know the stream helpers are JVM-only even though the core API is common.

## Reflection

Kotlin reflection (`kotlin.reflect`) is separate from Java reflection.
Use it when you need type-token access or property references at runtime.

```kotlin
inline fun <reified T> className(): String = T::class.simpleName ?: "Anonymous"
println(className<String>())
println(className<List<Int>>())

class User(val name: String, val age: Int)
val nameProp: KProperty1<User, String> = User::name
val user = User("Alice", 30)
println(nameProp.get(user))

if (String::class.isInstance("hello")) {
    println("value is a String")
}
```

Note: `kotlin-reflect` is a separate artifact.
On JVM, `T::class` works with just the stdlib, but `KProperty` access requires `kotlin-reflect` on the classpath.
Prefer reified inline functions when possible.
