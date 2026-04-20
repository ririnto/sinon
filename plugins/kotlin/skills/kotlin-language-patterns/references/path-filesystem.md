---
title: JVM Path and Filesystem Boundaries
description: >-
  Open this when a JVM filesystem boundary or kotlin.io.path usage is the blocker.
---

Use this reference when the job is to implement or review one JVM filesystem boundary in Kotlin. This reference should be sufficient on its own for that task.

Use this file to finish one of these jobs:

- choose between raw `String` paths and `Path`
- write or read a small JVM text file correctly
- stream or scan a large JVM file without loading it all at once
- handle one closeable JVM resource with `use {}`
- reason about `exists()`, parent creation, and normal filesystem helper behavior

`Path` rules:

- on JVM, prefer `java.nio.file.Path` plus `kotlin.io.path.*` extensions
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
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
    }
```

Large-file example:

```kotlin
import java.io.File

fun countErrors(logFile: File): Int {
    var count = 0
    logFile.forEachLine { line ->
        if ("ERROR" in line) {
            count += 1
        }
    }
    return count
}
```

Use this shape when the code needs path joining, parent creation, conditional first-write, filename inspection, or ordinary text I/O in one boundary.
