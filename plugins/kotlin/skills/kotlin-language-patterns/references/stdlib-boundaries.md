---
title: Platform and Experimental Stdlib Boundaries
description: >-
  Open this when platform-specific or experimental stdlib APIs need explicit caveats.
---

Use this reference when the job is to recommend a Kotlin stdlib API responsibly, with the right platform and stability caveats. This reference should be sufficient on its own for that decision.

Use this file to finish one of these jobs:

- decide whether an API is common, JVM-only, or platform-filtered
- decide whether an API is experimental enough to require an inline warning
- keep less-common stdlib surfaces from being presented as ordinary defaults
- explain why a JVM example should not be copied into multiplatform code unchanged

Best-practice rules:

- treat the Kotlin stdlib docs as versioned and platform-filtered; examples should read as `Common` first unless the code is intentionally runtime-specific
- `kotlin.io.path.*` is JVM-only and some APIs are marked `ExperimentalPathApi`; use it only when the module is explicitly on JVM and real filesystem `Path` behavior matters
- `kotlin.io.encoding` is experimental; use it only when encoding support is actually required, and remember that stream helpers there are JVM-only
- `kotlin.uuid` is experimental since Kotlin 2.0; use it only when UUID generation or parsing is genuinely needed
- `kotlin.contracts` is experimental and is not a common-path recommendation for ordinary application code
- `Regex` exists across platforms, but options and behavior can differ because JS uses the host `RegExp` behavior with stricter Unicode parsing
- examples that use `java.io.File`, `BufferedReader`, or other JDK resource types are JVM-specific illustrations even when the surrounding stdlib concept is broader
- stdlib `kotlin.coroutines` is the low-level coroutine surface; higher-level async work still belongs in `kotlinx.coroutines` guidance rather than this language-pattern entrypoint

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

Experimental API with inline warning:

```kotlin
/** Experimental stdlib API: reevaluate availability before using as a default. */
import kotlin.uuid.Uuid

fun createExperimentalId(): Uuid = Uuid.random()
```

Use when: the API surface is still experimental and callers need to know the example is not a default recommendation.

Experimental contracts with explicit non-default framing:

```kotlin
/** Experimental contracts API: use only when simpler null checks are not enough. */
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun requireNotNullName(name: String?) {
    contract { returns() implies (name != null) }
    require(name != null)
}
```

Use when: the team is intentionally opting into contracts and the example must make that non-default status obvious.

Experimental encoding helper with explicit status:

```kotlin
/** Experimental encoding API; stream helpers are JVM-only. */
import kotlin.io.encoding.Base64

fun encodeExperimental(raw: ByteArray): String = Base64.encode(raw)
```

Use when: the example needs encoding support, but the API should not be mistaken for the unconditional common-path recommendation.

## Reflection

Kotlin reflection (`kotlin.reflect`) is separate from Java reflection. Use it when you need type-token access or property references at runtime.

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

Note: `kotlin-reflect` is a separate artifact. On JVM, `T::class` works with just the stdlib, but `KProperty` access requires `kotlin-reflect` on the classpath. Prefer reified inline functions when possible.
