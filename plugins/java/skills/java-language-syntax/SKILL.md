---
name: java-language-syntax
description: >-
  Check Java syntax compatibility, migrate source between Java baselines, or choose foundational java.base APIs.
---

# Java Language Syntax

Explain Java syntax, LTS-boundary language differences, and foundational `java.base` coverage when it materially affects how code is written, read, or refactored.
The common case is checking the target Java LTS baseline first, then choosing the clearest stable syntax and the smallest baseline-safe standard-library surface available on that baseline.

## Operating rules

- MUST identify the target Java LTS version (`8`, `11`, `17`, `21`, or `25`) before recommending any version-sensitive syntax.
- MUST distinguish stable language features from preview-only or withdrawn features.
- SHOULD prefer stable syntax unless preview use is explicitly requested.
- MUST explain fallback forms when recommending syntax unavailable on the target baseline.
- MUST treat string templates as withdrawn (previewed in JDK 21 and 22, then withdrawn before JDK 23 instead of being finalized), not as a valid Java 25 default or modernization path.
- MUST keep version-difference guidance centered on LTS releases unless the user explicitly asks about a non-LTS release.
- MUST treat `java.base` guidance here as foundational standard-library coverage, not as a claim about broader Java SE modules or JDK tooling.
- SHOULD focus on syntax and expression differences that materially affect code shape.
- SHOULD prefer the smallest newer syntax that materially improves readability over mechanically replacing every older form.

## Task Context

Read the relevant source and build configuration to establish the target baseline.
Ask only when the baseline remains unclear and changes the recommendation.
Use [`advanced-syntax-recipes.md`](./references/advanced-syntax-recipes.md) for exact LTS availability, migration, or later-LTS recipes.
Use [`java-base-family-map.md`](./references/java-base-family-map.md) to select a foundational package family before adding dependencies.

### Version legend

- `(JDK 8+)` means safe on Java 8 and later LTS targets.
- `(JDK 11+)` means available on Java 11 and later.
- `(JDK 17+)`, `(JDK 21+)`, `(JDK 25+)` mean an LTS-boundary upgrade, not a universal fallback.
- If two examples solve the same problem, prefer the lowest-baseline version that still keeps the code clear.

## Syntax Examples

Version-aware switch comparison `(JDK 17+)` vs fallback `(JDK 8+)`:

```java
String result = switch (status) {
    case OK -> "ok";
    case FAIL -> "fail";
    default -> "unknown";
};
```

```java
String result;
switch (status) {
    case OK:
        result = "ok";
        break;
    case FAIL:
        result = "fail";
        break;
    default:
        result = "unknown";
}
```

Use when: you need a quick answer to "can I use this on Java X?"

Text block for multiline literals `(JDK 17+)`:

```java
String sql = """
    select *
    from users
    where active = true
    order by created_at desc
    """;
```

Record `(JDK 17+)`:

```java
record Point(int x, int y) {
}
```

## Ready-to-adapt templates

### Lambda expressions `(JDK 8+)`

Single-expression lambda:

```java
import java.util.Comparator;

Comparator<String> byLength = (a, b) -> Integer.compare(a.length(), b.length());
```

Block-body lambda:

```java
import java.util.function.Consumer;

Consumer<String> auditor = message -> {
    System.err.println(message);
    metrics.record(message.length());
};
```

Type-inferred lambda with functional interface:

```java
import java.util.function.Function;

Function<String, Integer> lengthOf = String::length;
```

Prefer a method reference when the lambda only forwards to one call and the target instance, overload, and evaluation timing stay identical.

### Method references `(JDK 8+)`

Static method reference:

```java
import java.util.function.Function;

Function<String, Integer> parser = Integer::parseInt;
```

Instance method reference on arbitrary object:

```java
import java.util.function.Function;

Function<String, Integer> lengthOf = String::length;
```

Bound instance method reference:

```java
import java.util.function.Consumer;

Consumer<String> printer = System.out::println;
```

Constructor reference:

```java
import java.util.ArrayList;
import java.util.function.Supplier;

Supplier<ArrayList<String>> newList = ArrayList::new;
ArrayList<String> buffer = newList.get();
```

### Stream pipeline (JDK 8+, `.toList()` requires JDK 16+)

Basic filter-map-collect using the unmodifiable `toList()` terminator `(JDK 16+)`:

```java
import java.util.List;

List<String> activeNames = users.stream()
    .filter(User::isActive)
    .map(User::name)
    .toList();
```

Older-LTS fallback using `Collectors.toList()` `(JDK 8+)` - the official javadoc makes no guarantee about the returned `List` implementation, its mutability, its serializability, or its thread-safety.
Current HotSpot builds happen to return a mutable `ArrayList`, but code MUST NOT rely on that.
When mutability matters, use `Collectors.toCollection(ArrayList::new)`.
When an unmodifiable result is part of the contract, use `Collectors.toUnmodifiableList()` (JDK 10+) or `Stream.toList()` (JDK 16+).

```java
import java.util.List;
import java.util.stream.Collectors;

List<String> activeNames = users.stream()
    .filter(User::isActive)
    .map(User::name)
    .collect(Collectors.toList());
```

Grouping and counting `(JDK 8+)`:

```java
import java.util.Map;
import java.util.stream.Collectors;

Map<String, Long> countByRole = users.stream()
    .collect(Collectors.groupingBy(User::role, Collectors.counting()));
```

Flat map for nested collections (uses `Stream.toList()`, so target `(JDK 17+)`):

```java
import java.util.List;

List<String> allTags = orders.stream()
    .flatMap(order -> order.tags().stream())
    .distinct()
    .toList();
```

### `Optional` pipeline `(JDK 8+)`

```java
import java.util.Optional;

Optional<Integer> timeout = Optional.ofNullable(config)
    .map(Config::timeout)
    .filter(t -> t > 0);
```

### Immutable collection factory `(JDK 9+, practical LTS: JDK 11+)`

```java
import java.util.List;
import java.util.Map;
import java.util.Set;

List<String> roles = List.of("reader", "writer");
Set<String> perms = Set.of("read", "write");
Map<String, Integer> scores = Map.of("alice", 90, "bob", 85);
```

### `CompletableFuture` async composition `(JDK 8+)`

Basic async chain:

```java
import java.util.concurrent.CompletableFuture;

CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> fetchUser(id))
    .thenApply(User::name)
    .thenCompose(name -> fetchAvatar(name))
    .exceptionally(ex -> "default-avatar");
```

Combine multiple futures:

```java
import java.util.concurrent.CompletableFuture;

CompletableFuture<String> combined = CompletableFuture.supplyAsync(() -> fetchUser(id))
    .thenCombine(CompletableFuture.supplyAsync(() -> fetchPerms(id)), (user, perms) -> user + ":" + perms);
```

### `HttpClient` API `(JDK 11+)`

Synchronous request:

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

HttpResponse<String> response = HttpClient.newHttpClient()
    .send(
        HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/data"))
            .header("Accept", "application/json")
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString()
    );
```

Asynchronous request:

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

CompletableFuture<HttpResponse<String>> future = HttpClient.newHttpClient()
    .sendAsync(
        HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/data"))
            .header("Accept", "application/json")
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString()
    );
```

### String convenience methods `(JDK 11+)`

```java
boolean blank = "  ".isBlank();
String stripped = "  hello  ".strip();
String repeated = "ha".repeat(3);
```

### Default and static interface methods `(JDK 8+)`

```java
interface LogFormatter {
    String format(String message);

    default String formatWithPrefix(String prefix, String message) {
        return prefix + format(message);
    }

    static LogFormatter prefixed(String prefix) {
        return message -> prefix + message;
    }
}
```

### Switch expression with `yield` `(JDK 17+)`

```java
int code = switch (status) {
    case OK -> 0;
    case FAIL -> {
        System.err.println("failure detected");
        yield 1;
    }
    default -> -1;
};
```

### Sealed hierarchy `(JDK 17+)`

```java
sealed interface PaymentResult permits Approved, Rejected {
}

record Approved(String authorizationId) implements PaymentResult {
}

record Rejected(String reason) implements PaymentResult {
}
```

### Pattern matching for `instanceof` `(JDK 17+)`

```java
if (obj instanceof String s) {
    use(s);
}
```

### Pattern-matching switch `(JDK 21+)`

Exhaustive over a sealed hierarchy.
The permitted subtypes are enumerated, so no `default` is needed:

```java
sealed interface Shape permits Circle, Rectangle {
}

record Circle(double radius) implements Shape {
}

record Rectangle(double width, double height) implements Shape {
}

double area = switch (shape) {
    case Circle c -> Math.PI * c.radius() * c.radius();
    case Rectangle r -> r.width() * r.height();
};
```

Switch with guarded patterns (`when` clause) `(JDK 21+)`:

```java
String label = switch (value) {
    case String s when s.length() > 10 -> s.substring(0, 7) + "...";
    case String s -> s;
    case Integer i -> "int:" + i;
    default -> "unknown";
};
```

### Sequenced collections `(JDK 21+)`

```java
import java.util.ArrayList;
import java.util.List;
import java.util.SequencedCollection;

SequencedCollection<String> items = new ArrayList<>(List.of("a", "b", "c"));
String first = items.getFirst();
String last = items.getLast();
SequencedCollection<String> reversed = items.reversed();
items.addFirst("z");
items.addLast("d");
```

### Unnamed pattern `(preview JDK 21, final JDK 22+)`

```java
if (obj instanceof Order(String id, _, double total)) {
    audit(id, total);
}
```

### Local variable inference `(JDK 11+)`

```java
import java.util.List;
import java.util.function.Predicate;

var count = List.of("alice", "bob", "carol").size();
Predicate<String> lengthOver3 = (var name) -> name.length() > 3;
```

Notes:

- `var` is for local variables.
  - It does not change runtime types.
- Use of `var` inside lambda parameter lists (`(var name) -> ...`) requires JDK 11 or later.

### Classic switch fallback `(JDK 8+)`

```java
String result;
switch (status) {
    case OK:
        result = "ok";
        break;
    case FAIL:
        result = "fail";
        break;
    default:
        result = "unknown";
}
```

## Edge cases

- If repository evidence does not establish the required Java baseline, clarify it before recommending version-sensitive syntax.
- Questions about API shape or type modeling are outside this skill's scope.
  Use `java:java-language-design`.
- Questions about JUnit structure are outside this skill's scope.
  Use `java:java-test`.
- Questions about profiling or concurrency are outside this skill's scope.
  Use `java:java-performance-concurrency`.
- Questions about Maven coordinate lookup are outside this skill's scope.
  Use `java:java-dependency-versioning`.
- If a preview feature is requested, state the support cost and baseline requirement explicitly before including it in guidance.
- If `java.base` drifts toward `jdk.*` tools, `jdeps`, `jlink`, `jpackage`, runtime images, packaging chains, or live JVM diagnostics, stop and clarify that those are outside this skill's scope.

## Result

State the baseline, recommended syntax, and any relevant fallback or preview limitation.
For source edits, verify compatibility with the repository's affected native compile or test task.
