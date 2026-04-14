---
title: Java Syntax Version Notes
description: >-
  Reference for Java syntax availability and LTS-boundary version notes.
---

Use this reference when syntax availability depends on the Java baseline.

## How to Use This Reference

1. Identify the target Java LTS first.
2. Start from the nearest LTS boundary below or equal to that baseline.
3. Prefer syntax that is stable at that boundary.
4. If you recommend syntax from a newer non-LTS release, call that out explicitly and explain why the extra requirement is worth it.

## LTS Boundary Syntax Notes

### Java 8 LTS Baseline

- Lambda expressions — `x -> x + 1`
- Method references — `System.out::println`
- Classic `switch` statements, ordinary classes, and explicit local types remain the baseline style.

Use this as the fallback target for older modern Java code. If the code must stay on Java 8, avoid `var`, switch expressions, text blocks, records, sealed classes, and pattern matching.

### Java 11 LTS Boundary

- Local variable type inference with `var`
- `var` in lambda parameters when the whole parameter list uses it consistently

Preferred use:

```java
var count = users.size();
```

Use `var` only for local variables with an initializer and only when the type remains obvious from the right-hand side. Keep explicit types when they carry meaning the initializer does not.

### Java 17 LTS Boundary

By this boundary, several now-standard syntax improvements are available together:

- Switch expressions
- Text blocks
- Records
- Pattern matching for `instanceof`
- Sealed classes

Preferred forms:

```java
var result = switch (status) {
    case OK -> "ok";
    case FAIL -> "fail";
    default -> "unknown";
};
```

```java
var sql = """
    select *
    from users
    where active = true
    """;
```

```java
record Point(int x, int y) {}
```

```java
if (obj instanceof String s) {
    use(s);
}
```

```java
sealed interface Shape permits Circle, Rectangle {}
```

Use this boundary when you want modern Java syntax that still fits a common enterprise baseline.

### Java 21 LTS Boundary

By this boundary, deconstruction and exhaustive matching become much more practical defaults:

- Record patterns
- Pattern matching for `switch`

Preferred forms:

```java
return switch (shape) {
    case Circle c -> c.radius();
    case Rectangle r -> r.width() * r.height();
};
```

```java
if (obj instanceof Point(int x, int y)) {
    use(x, y);
}
```

Use these forms when deconstruction or exhaustive matching makes the code shorter and clearer than manual casts plus getters.

### Java 25 LTS Boundary

By this boundary, only actually-stable cleanup syntax should be treated as a default on a current LTS target:

- Unnamed variables and unnamed patterns
- Compact source files
- Flexible constructor bodies

Preferred form when a binding is intentionally unused:

```java
if (obj instanceof Point(int x, int _)) {
    use(x);
}
```

Use these forms only when the unused value truly adds no meaning and the codebase actually targets this newer LTS boundary.

Additional Java 25 directions:

- Compact source files are suitable for script-like or small entry-point use cases, not as a forced replacement for ordinary multi-file application structure.
- Flexible constructor bodies are available at this boundary and should be considered when constructor validation or setup reads more clearly before field assignment completes.

## Preview and Non-Default Directions

Treat the following as opt-in only.

- Primitive patterns in `switch`, `instanceof`, and related contexts remain version-sensitive and should not be treated as default production syntax.
- Module import declarations remain preview-sensitive and should not be treated as default production syntax.
- String templates were previewed but not finalized as a stable Java feature.

Do not recommend preview or withdrawn syntax as a default modernization path.

## Migration Heuristics

- Java 8 target: prefer explicit types, classic switch, ordinary classes, and ordinary casts.
- Java 11 target: `var` is available, but records and switch expressions are not stable yet.
- Java 17 target: switch expressions, text blocks, records, sealed classes, and `instanceof` patterns are all reasonable defaults.
- Java 21 target: record patterns and switch pattern matching become realistic defaults.
- Java 25 target: unnamed bindings, compact source files, and flexible constructor bodies become stable options, but preview-only features should still be marked explicitly.

## Review Questions

- What is the repository’s actual Java LTS baseline?
- Is the recommended syntax stable on that baseline?
- Is there a simpler fallback form for older Java versions?
- Does the newer syntax materially improve clarity, or is it only newer?
