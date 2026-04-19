---
title: Java Base Family Map
description: >-
  Purpose-oriented lookup guide for choosing foundational java.base package families.
---

Use this reference when the question is no longer just “is this syntax valid?” and has become “which foundational `java.base` family should I reach for first?”

## Choose the Right Family

### Core runtime and metadata

- `java.lang` for core language types, runtime control, threads, and system APIs.
- `java.lang.annotation` and `java.lang.reflect` for runtime metadata, reflective inspection, and dynamic access.
- `java.lang.constant`, `java.lang.invoke`, `java.lang.module`, and `java.lang.runtime` for constants, method-handle/bootstrap linkage, modules, and lower-level runtime support.

Use when: the code question is about runtime structure, reflection, or module-aware behavior rather than packaging or tooling.

### Collections, utility, and text

- `java.util` for collections and general-purpose utilities.
- `java.util.function` and `java.util.stream` for functional pipelines.
- `java.util.regex` for pattern-driven parsing.
- `java.util.random` for random generators.
- `java.text` for locale-sensitive text and number formatting.

Use when: ordinary collection shaping, immutable factories, formatting, or regex parsing should stay inside the standard library.

Representative examples:

```java
var roles = List.of("reader", "writer");
```

```java
Pattern issuePattern = Pattern.compile("([A-Z]+)-(\\d+)");
```

### Time and date

- `java.time` and subpackages for timestamps, local dates, civil time, temporal arithmetic, and zones.

Use when: you need to distinguish precise instants from date-only or wall-clock business concepts.

Representative example:

```java
record Invoice(Instant issuedAt, LocalDate dueDate) {
}
```

### Files, I/O, and networking

- `java.io` for classic streams, readers, writers, and legacy file APIs.
- `java.nio`, `java.nio.channels`, and `java.nio.charset` for buffers, channels, and charset conversion.
- `java.nio.file` and `java.nio.file.attribute` for `Path`, `Files`, file traversal, metadata, and filesystem access.
- `java.net` for sockets, interfaces, URIs/URLs, and network addressing.

Use when: program structure still fits normal standard-library I/O rather than JDK packaging or runtime-image tooling.

Representative example:

```java
Path config = root.resolve("app.json");
Files.writeString(config, payload);
String loaded = Files.readString(config);
```

### Security and specialized runtime surfaces

- `java.security*`, `javax.crypto*`, `javax.net*`, and `javax.security.auth*` for providers, certificates, crypto, SSL, and authentication.
- `java.lang.classfile*` and `java.lang.foreign` for classfile transformation and foreign memory/function access.

Use when: the code genuinely needs these specialized capabilities. These are not the default path for ordinary application code.

### SPI and extension points

- `java.nio.file.spi`, `java.nio.charset.spi`, `java.nio.channels.spi`, and `java.net.spi` for provider hooks and extension points.

Use when: the problem is explicitly about customizing or extending standard-library behavior rather than simply using it.

## Boundary Reminder

Stay in this reference only for foundational `java.base` guidance. `jdeps`, `jlink`, `jpackage`, runtime images, packaging chains, and live JVM diagnostics are outside this skill's scope.
