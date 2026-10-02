---
description: >-
  Open this when a JVM filesystem boundary or kotlin.io.path usage is the blocker.
---

# JVM Path and Filesystem Boundaries

Choose the JVM filesystem operation for one boundary:

- choose between raw `String` paths and `Path`
- write or read a small JVM text file correctly
- stream or scan a large JVM file without loading it all at once
- handle one closeable JVM resource with `use {}`
- reason about `exists()`, parent creation, and normal filesystem helper behavior

On the JVM, `Path` is the JDK type from `java.nio.file`.
Kotlin's standard library adds `kotlin.io.path.*` extensions for common path composition, metadata, text I/O, directory creation, and line iteration.
Prefer these extensions when they provide the needed operation.
Use `java.nio.file.Files` when the standard library has no equivalent or when its options are required.
Use `root / "child"` for path composition through `kotlin.io.path.div`, available since Kotlin 1.5.

`Path` rules:

- use `java.nio.file.Path` as the JVM path type and prefer `kotlin.io.path.*` extensions for common operations
- compose paths with `kotlin.io.path.div` (`root / "child"`) when targeting Kotlin 1.5 or later
- prefer `Path` over raw `String` when joins, normalization, file names, or extensions matter
- `readText()` and `writeText()` default to UTF-8
- `readText()` is for normal-sized files, not unknown huge files
- `createDirectories()` is safe when the directory already exists
- `createParentDirectories()` is for a file path whose parent directories may not exist yet
- `exists()` returns `false` both when the file is absent and when existence cannot be determined

JVM `Path` example:

```kotlin
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.extension
import kotlin.io.path.createParentDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ConfigWriter {
    fun writeDefaultConfig(root: Path): Path {
        val out = root / "config" / "app.json"
        out.createParentDirectories()
        if (!out.exists()) {
            out.writeText("{}")
        }
        println(out.name)
        println(out.extension)
        println(out.readText())
        return out
    }
}
```

Resource-handling example:

```kotlin
import java.io.BufferedReader
import java.io.StringReader

fun firstNonBlankLine(raw: String): String? =
    BufferedReader(StringReader(raw)).use { reader ->
        reader.lineSequence()
            .map(String::trim)
            .firstOrNull(String::isNotEmpty)
    }
```

Large-file example (streams the file line by line without loading it all at once):

```kotlin
import java.nio.file.Path
import kotlin.io.path.useLines

fun countErrors(logFile: Path): Int =
    logFile.useLines { lines ->
        lines.count { line -> "ERROR" in line }
    }
```

Use this shape when the code needs path joining, parent creation, conditional first-write, filename inspection, or ordinary text I/O in one boundary.
