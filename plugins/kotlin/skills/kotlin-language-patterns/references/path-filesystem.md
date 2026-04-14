---
title: Path and Filesystem Guide
description: >-
  Reference for JVM Path usage, kotlin.io.path helpers, and ordinary filesystem-boundary decisions in Kotlin code.
---

Use this reference when the module already clearly needs JVM file APIs and the remaining blocker is exact `Path` helper choice or filesystem-boundary behavior.

`Path` rules:

- on JVM, prefer `java.nio.file.Path` plus `kotlin.io.path.*` extensions
- prefer `Path` over raw `String` when joins, normalization, file names, or extensions matter
- `readText()` and `writeText()` default to UTF-8
- `readText()` is for normal-sized files, not unknown huge files
- `createDirectories()` is safe when the directory already exists
- `createParentDirectories()` is for a file path whose parent directories may not exist yet
- `exists()` returns `false` both when the file is absent and when existence cannot be determined

Use this shape when the code needs path joining, parent creation, conditional first-write, filename inspection, or ordinary text I/O in one boundary.
