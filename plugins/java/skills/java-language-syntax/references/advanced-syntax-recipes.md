---
title: Advanced Java Syntax Recipes
description: >-
  Version-sensitive Java syntax guide for LTS lookup, migration framing, and advanced recipes.
---

Use this reference when `SKILL.md` handles the common case but you still need one of these deeper jobs:

- confirm exact syntax availability on a Java LTS boundary
- compare upgrade or downgrade implications across Java 8, 11, 17, 21, and 25
- reach for a later-LTS or less-common syntax recipe that should not live in the main entrypoint

## Quick LTS Availability Table

| Syntax or API shape | First stable LTS |
| --- | --- |
| lambda expressions, method references, default/static interface methods | Java 8 |
| `var` for locals | Java 11 |
| `var` in lambda parameters | Java 11 |
| `module-info.java` | Java 11 |
| switch expressions | Java 17 |
| text blocks | Java 17 |
| records | Java 17 |
| pattern matching for `instanceof` | Java 17 |
| sealed classes and interfaces | Java 17 |
| record patterns | Java 21 |
| pattern matching for `switch` | Java 21 |
| unnamed variables and unnamed patterns | Java 25 |
| module import declarations | Java 25 |
| compact source files | Java 25 |
| flexible constructor bodies | Java 25 |

## LTS Boundary Notes

### Java 8 LTS

- Treat this as the conservative fallback boundary.
- Prefer explicit local types, classic `switch`, ordinary classes, and manual casts.
- Avoid framing `var`, records, sealed hierarchies, text blocks, or pattern matching as baseline-safe here.

### Java 11 LTS

- `var` becomes available for locals and lambda parameters.
- `module-info.java` is available, but JPMS should still be opt-in rather than assumed.
- Records, switch expressions, and pattern matching are still not stable on this LTS.

### Java 17 LTS

- This is the biggest “modern Java” boundary for everyday code shape.
- Switch expressions, text blocks, records, sealed classes, and `instanceof` pattern matching can all be treated as realistic defaults.
- Prefer this boundary when you want modern syntax without requiring a cutting-edge runtime.

### Java 21 LTS

- Record patterns and pattern-matching `switch` make deconstruction and exhaustive branching more practical.
- Use this boundary when matching logic is genuinely clearer than casts plus getters.

### Java 25 LTS

- The main additions are cleanup and ergonomics rather than a new programming model.
- Unnamed variables and unnamed patterns become the most generally useful additions.
- Module import declarations, compact source files, and flexible constructor bodies are available, but should still be treated as deliberate style choices rather than default modernization.

## Migration Heuristics

- Java 8 → 11: adopt `var` only where the right-hand side keeps the type obvious.
- Java 11 → 17: modernize `switch`, multiline literals, and data-carrier types first.
- Java 17 → 21: use record patterns and pattern-matching `switch` only when they materially simplify matching logic.
- Java 21 → 25: adopt unnamed bindings only when ignored values truly add no meaning.

## Java 11 Recipes

### `var` in lambda parameters `(JDK 11+)`

```java
Function<String, String> normalize = (@Nonnull var input) -> input.trim().toLowerCase();
```

Use when: the whole parameter list needs modifiers or annotations and you still want inferred local syntax.

### Try-with-resources with effectively final values `(JDK 11+)`

```java
BufferedReader reader = Files.newBufferedReader(path);
try (reader) {
    return reader.readLine();
}
```

Use when: the resource already exists as a local variable and you want concise cleanup without re-declaring it.

## Java 17 Recipes

### Record with canonical constructor `(JDK 17+)`

```java
record Money(String currency, long cents) {
    Money {
        Objects.requireNonNull(currency);
        if (cents < 0) {
            throw new IllegalArgumentException("cents must be >= 0");
        }
    }
}
```

Use when: a record is still the right model, but it needs compact invariant checks.

### Closed hierarchy with records `(JDK 17+)`

```java
sealed interface Shape permits Circle, Rectangle {
}

record Circle(double radius) implements Shape {
}

record Rectangle(double width, double height) implements Shape {
}
```

Use when: a closed domain benefits from both exhaustive matching and lightweight immutable carriers.

## Java 21 Recipes

### Record pattern deconstruction `(JDK 21+)`

```java
if (obj instanceof Point(int x, int y)) {
    use(x, y);
}
```

Use when: record components are needed immediately and deconstruction is clearer than accessor calls.

## Java 25 Recipes

### Module import declaration `(JDK 25+)`

```java
import module java.sql;
```

Use when: the project explicitly chooses this newer module-oriented style. Do not treat it as the default shape for ordinary application code.

### Flexible constructor body `(JDK 25+)`

```java
class Account {
    private final String id;

    Account(String id) {
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id;
    }
}
```

Use when: earlier validation or setup reads more clearly before all field assignments complete.

## Preview and Withdrawn Notes

- Primitive-type patterns still need explicit preview framing even on Java 25.
- String templates should not be presented as default guidance because they were previewed and then withdrawn instead of finalized.
