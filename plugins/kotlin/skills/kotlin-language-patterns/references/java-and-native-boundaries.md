# Java And Native Boundaries

## Keep Java callers in view

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

## Keep Kotlin-native boundaries explicit

Keep adjacent Kotlin-native boundaries in this skill even when their detailed implementation moves to references.

- use `kotlinx.serialization` when the boundary is Kotlin-first model encoding or decoding
- use `kotlinx.datetime.Instant` for real moments in time on the Kotlin 2.1 baseline (stdlib `kotlin.time.Instant` is stable since Kotlin 2.3), and keep `LocalDate`, `LocalDateTime`, and `TimeZone` in `kotlinx-datetime`
- use the JVM `Path` type with Kotlin `kotlin.io.path.*` extensions for common filesystem operations
