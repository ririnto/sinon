---
name: java-language-syntax
description: >-
  Explain Java syntax availability across LTS baselines, compare expression forms between Java versions,
  rewrite code for older or newer targets, and choose foundational java.base package families.
  Use when the user asks about Java grammar, var, switch expressions, records, pattern matching,
  sealed classes, text blocks, unnamed patterns, or whether a syntax form compiles on a given Java baseline.
---

# Java Language Syntax

Explain Java syntax, LTS-boundary language differences, and foundational `java.base` coverage when it materially affects how code is written, read, or refactored. The common case is checking the target Java LTS baseline first, then choosing the clearest stable syntax and the smallest baseline-safe standard-library surface available on that baseline.

## Operating rules

- MUST identify the target Java LTS version (`8`, `11`, `17`, `21`, or `25`) before recommending any version-sensitive syntax.
- MUST distinguish stable language features from preview-only or withdrawn features.
- SHOULD prefer stable syntax unless preview use is explicitly requested.
- MUST explain fallback forms when recommending syntax unavailable on the target baseline.
- MUST treat string templates as withdrawn (previewed in JDK 21-23, then withdrawn instead of being finalized), not as a valid Java 25 default or modernization path.
- MUST keep version-difference guidance centered on LTS releases unless the user explicitly asks about a non-LTS release.
- MUST treat `java.base` guidance here as foundational standard-library coverage, not as a claim about broader Java SE modules or JDK tooling.
- SHOULD focus on syntax and expression differences that materially affect code shape.
- SHOULD prefer the smallest newer syntax that materially improves readability over mechanically replacing every older form.

## Procedure

1. Identify the target Java LTS baseline from the project build configuration, or ask the user if ambiguous.
2. Read the current code and classify the question: stable syntax, preview syntax, migration compatibility, or foundational `java.base` usage.
3. Prefer stable language features by default and call out preview-only or withdrawn constructs explicitly.
4. When the question reaches into `java.base`, anchor to foundational families such as collections, time, files, regex, or `Optional` before suggesting extra dependencies.
5. Recommend the smallest syntax or library-shape change that improves clarity while remaining compatible with the target baseline.
6. Keep fallback shapes visible for older baselines when the recommendation depends on `17`, `21`, or `25`.

### Version legend

- `(JDK 8+)` means safe on Java 8 and later LTS targets.
- `(JDK 11+)` means available on Java 11 and later.
- `(JDK 17+)`, `(JDK 21+)`, `(JDK 25+)` mean an LTS-boundary upgrade, not a universal fallback.
- If two examples solve the same problem, prefer the lowest-baseline version that still keeps the code clear.

## First runnable commands

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

Consumer<String> logger = message -> {
    System.err.println(message);
};
```

No-argument lambda:

```java
Runnable task = () -> runCleanup();
```

Type-inferred lambda with functional interface:

```java
import java.util.function.Function;

Function<String, Integer> lengthOf = String::length;
```

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
import java.util.List;
import java.util.stream.Collectors;

List<ArrayList<String>> lists = Stream.of("a", "b")
    .map(s -> new ArrayList<>())
    .toList();
```

### Stream pipeline `(JDK 8+)`

Basic filter-map-collect:

```java
import java.util.List;
import java.util.stream.Collectors;

List<String> activeNames = users.stream()
    .filter(User::isActive)
    .map(User::name)
    .toList();
```

Grouping and counting:

```java
import java.util.Map;
import java.util.stream.Collectors;

Map<String, Long> countByRole = users.stream()
    .collect(Collectors.groupingBy(User::role, Collectors.counting()));
```

Flat map for nested collections:

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

CompletableFuture<String> userFuture = CompletableFuture.supplyAsync(() -> fetchUser(id));
CompletableFuture<String> permFuture = CompletableFuture.supplyAsync(() -> fetchPerms(id));

CompletableFuture<String> combined = userFuture.thenCombine(permFuture,
    (user, perms) -> user + ":" + perms);
```

### `HttpClient` API `(JDK 11+)`

Synchronous request:

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/data"))
    .header("Accept", "application/json")
    .GET()
    .build();
HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
```

Asynchronous request:

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

CompletableFuture<HttpResponse<String>> future = client.sendAsync(
    request, HttpResponse.BodyHandlers.ofString());
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

```java
double area = switch (shape) {
    case Circle c -> c.radius();
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

### Unnamed pattern `(JDK 25+)`

```java
if (obj instanceof Order(String id, _, double total)) {
    audit(id, total);
}
```

### Local variable inference `(JDK 11+)`

```java
int count = users.size();
```

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

- If the user does not specify a Java baseline, ask before recommending any version-sensitive syntax.
- If the question is about API shape, type modeling, or exception contracts rather than syntax availability, that is outside this skill's scope.
- If the question is about JUnit structure or test-first workflow, that is outside this skill's scope.
- If the question is about profiling, concurrency, or virtual threads, that is outside this skill's scope.
- If the question is about Maven coordinate lookup, that is outside this skill's scope.
- If a preview feature is requested, state the support cost and baseline requirement explicitly before including it in guidance.
- If `java.base` drifts toward `jdk.*` tools, `jdeps`, `jlink`, `jpackage`, runtime images, packaging chains, or live JVM diagnostics, stop and clarify that those are outside this skill's scope.

## Output contract

Return:

1. The target Java LTS baseline used for the recommendation.
2. The recommended syntax form with version annotation.
3. A compatible fallback form when the target baseline is older than the recommendation.
4. Explicit note if any construct is preview or withdrawn on the target baseline.

## Support-file pointers

| If the blocker is... | Open... |
| --- | --- |
| exact LTS-boundary availability, migration heuristics, or later-LTS recipes | [`advanced-syntax-recipes.md`](./references/advanced-syntax-recipes.md) |
| choosing a foundational `java.base` package family | [`java-base-family-map.md`](./references/java-base-family-map.md) |

## Gotchas

- Do not suggest syntax without naming the Java baseline.
- Do not treat preview features as default modernization.
- Do not treat string templates as a stable Java 25 feature.
- Do not replace every old form with a new one mechanically.
- Do not omit a fallback for older baselines.
- Do not treat `java.base` as if it covered all `jdk.*` tools or every Java SE module.
