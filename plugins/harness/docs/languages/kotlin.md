---
metadata:
  reference:
    Kotlin Coding Conventions:
      url: https://kotlinlang.org/docs/_llms/coding-conventions.txt
    Kotlin Callable References:
      url: https://kotlinlang.org/docs/reflection.html#callable-references
    Kotlin Java Interoperability:
      url: https://kotlinlang.org/docs/java-interop.html#getters-and-setters
    Kotlin Equality:
      url: https://kotlinlang.org/docs/equality.html#referential-equality
    Kotlin Null Preconditions:
      url:
        - https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/require-not-null.html
        - https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/check-not-null.html
    Kotlin Regex Conversion:
      url: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/to-regex.html
    Kotlin Scope Functions:
      url:
        - https://kotlinlang.org/docs/_llms/scope-functions.txt
        - https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/take-if.html
    Kotest:
      version: 6.2.5
      url:
        - https://kotest.io/docs/framework/project-setup.html
        - https://kotest.io/docs/framework/testing-styles.html
        - https://kotest.io/docs/assertions/core-matchers.html
    Kotlin Standard Library Path API:
      url:
        - https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/
        - https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/-path.html
    Kotlin Path div Operator:
      url: https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/div.html
    Java NIO Files API:
      version: "25"
      url: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Files.html
    Java File API:
      version: "25"
      url: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/File.html
    Java NIO Path API:
      version: "25"
      url:
        - https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Path.html
        - https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Paths.html
    ktlint:
      version: "1.8.0"
      url:
        - https://raw.githubusercontent.com/ktlint/ktlint/1.8.0/ktlint-rule-engine-core/src/main/kotlin/com/pinterest/ktlint/rule/engine/core/api/RuleAutocorrectApproveHandler.kt
        - https://raw.githubusercontent.com/ktlint/ktlint/1.8.0/ktlint-rule-engine/src/main/kotlin/com/pinterest/ktlint/rule/engine/api/KtLintRuleEngine.kt
---

# Kotlin Rules

These rules extend the [common rules](../rules.md) for Kotlin files.
Read them together with that document.
The [Gradle tool reference](../tools/gradle.md) owns commands and configuration.

## Effective Visibility And Documentation Comments

Treat a declaration as public when it is visible outside its module.
Account for enclosing scopes.
An `internal` or `private` scope limits declarations inside it.
Every effective public or protected declaration carries a KDoc comment.
Write KDoc in English with multiple lines.
Put the opening `/**` and closing `*/` delimiters on separate lines, including for one-sentence KDoc.
Write meaningful English sentences on lines beginning with `*`.
Concise KDoc states the contract, not a restatement of the name.
A purely private helper needs no KDoc.

## Function Bodies And Comments

Use an expression body for one returned expression when return type, `Unit` behavior, nullability, and API semantics stay unchanged.
Split pure transformations into readable chain steps.
Prefer callable references when they preserve meaning, types, overload resolution, and receiver binding.
Apply this preference to lambdas that only access one property or call its getter.
For Java PSI parents, use `PsiElement::getParent` when its type matches the required function.
For example, use `map(String::trim)`, `filterNot(String::isEmpty)`, or `map(::UserId)`.
Other useful steps include `take(500)` and `toList()`.
Keep a lambda when it transforms an argument, such as `map { line -> line.substringBefore('#') }`.
Keep an explicit named lambda where an extra bound argument is required.
Split a pure `&&` collection predicate into chained `filter` calls when each condition can stand alone.
Chain independent positive `takeIf` predicates for one object, using `?.takeIf` at every nullable step.
Keep the conditions in their original left-to-right order.
Keep predicates together when later conditions need earlier smart casts.
Keep them together when splitting changes nullability, short-circuit behavior, effects, exception timing, eager work, allocations, or required performance.
Keep `takeUnless` and `filterNot` predicates intact.
Do not split mixed-polarity predicates when that changes their Boolean logic.
Use a `Sequence` only when lazy evaluation matches the contract.
For optional nullable processing, prefer `?.let` with a named non-null parameter.
Use the captured parameter instead of rereading the nullable property.
Do not replace required validation or failure behavior with silently skipped processing.
Keep terminal returns when they express the function's result.
Minimize mid-block `if` branches with `return`, `break`, or `continue`.
Invert the condition or move a loop exit into the loop condition when behavior and evaluation order remain unchanged.
Put braces around every `if` and `else` branch, including expression branches, guard returns, and one-line branches.
Write function and constructor argument lists without a trailing comma.
Keep short calls on one line when they fit the configured line limit.

## Registered Components And Required Values

Treat a class that a container or DI framework constructs and registers as a bean.
Declare every constructor parameter that receives a dependency or a configuration value non-null and without a default value.
A required value missing at registration is a startup failure, not a nullable property or a silent code fallback.
Keep configuration values in configuration sources, not code defaults.
In Spring Boot, declare bound parameters in `@ConfigurationProperties` classes.
Supply their values in `application.yaml`.
Express genuinely optional behavior as explicit strategy implementations selected at composition time instead of a nullable or defaulted dependency.

## Bindings And Lambdas

Use `val` when a value does not need reassignment.
Follow common rules when converting a `var` to `val`.
Use `=== null` and `!== null` for null checks to make reference identity explicit.
This is a repository style rule.
Kotlin also treats `== null` as equivalent to a reference identity check.
The `harness:null-comparison-identity` rule reports `==` or `!=` when either operand is a literal null.
It preserves structural equality for other values and does not apply automatic corrections.
Prefer `requireNotNull` for required arguments and `checkNotNull` for required state over Boolean null preconditions.
Keep the original exception type, message, evaluation order, wrapper contract, and `Unit` return type.
`requireNotNull` throws `IllegalArgumentException`, while `checkNotNull` throws `IllegalStateException` for null values.
Name every lambda parameter explicitly for its role instead of the implicit `it`, with no exception for short lambdas.
Use `_` only for a genuinely unused parameter.
Do not confuse the Kotest DSL form `it("description")` with an implicit lambda parameter.
Use extension functions when ownership or the call site benefits.
Do not add factories, DSLs, or layers for appearance.

## Raw Strings

Use raw strings (`"""`) for regular expressions and JSON fixtures.
Use raw triple-quoted strings with `trimIndent()` for multiline code text instead of escaped newline strings.
Preserve the intended indentation, newline data, and interpolation when changing string form.
Prefer direct string helpers before regular expressions.
Use `String.toRegex()` when a regular expression is required.
Know whether the trailing newline before the closing delimiter is part of the value, and match the exact target.

## Filesystem Paths

Keep `java.nio.file.Path` as the path type and use `kotlin.io.path` extensions for supported filesystem operations.
Import `kotlin.io.path.div` and compose a child path with `base / child` when it is equivalent to `base.resolve(child)`.
The `Path.div` overloads accept a `Path` or a `String` and are operator functions, not infix functions.
Do not convert a `Path` to `java.io.File` for `exists`, `isDirectory`, `mkdirs`, or zero-argument `listFiles()` checks.
Use `Path.exists()`, `Path.isDirectory()`, `Path.createDirectories()`, and `Path.listDirectoryEntries()` for equivalent operations.
`File.mkdirs()` returns a `Boolean`, and `File.listFiles()` may return `null`.
`Path.createDirectories()` returns a `Path` or throws, while `Path.listDirectoryEntries()` throws on failure.
`File.listFiles(FileFilter)` and `listFiles(FilenameFilter)` have no direct equivalent in `listDirectoryEntries()`.
Keep filtered overloads when their callback semantics matter.
Preserve the original result and failure behavior when replacing either `File` call.
Prefer `kotlin.io.path` equivalents for `Files.createDirectories`, `Files.deleteIfExists`, `Files.exists`, and `Files.isDirectory`.
Use equivalents for `Files.isHidden`, `Files.isRegularFile`, and `Files.isSymbolicLink` when behavior stays equivalent.
Also prefer them for `Files.readAllBytes`, `Files.readString`, and `Files.writeString` when behavior stays equivalent.
Keep Java NIO operations when no Kotlin path extension provides the required behavior.
Keep Java methods when their options or resource semantics matter.
The `harness:no-java-path-api` rule reports listed helper calls through direct, qualified, aliased, and static imports.
It ignores helper names that resolve to local parameters, properties, functions, or destructured declarations.
It reports `Path.resolve(String)` and `Path.resolve(Path)` on scoped Java `Path` bindings.
It recognizes explicitly typed bindings and supported factory initializers from `Path.of`, `Paths.get`, or `kotlin.io.path.Path`.
It follows Java methods with recognized argument signatures that return `Path`.
It uses syntax and scoped bindings without compiler symbol resolution.
It skips unknown receiver chains, unsupported arguments, and shadowed factory names.
It leaves `Path.toUri().resolve(...)` unchanged because `URI.resolve` has different semantics.
It does not report `Path` declarations or a `Files` import by itself.
It does not report `resolve` calls on unrelated receivers or Java NIO operations without listed Kotlin equivalents.

## Control Flow And Subject Branches

Use `when (subject)` for a complete branch chain that compares the same subject with `==` or `is`.
Keep arbitrary boolean conditions, reversed equality operands, and negated comparisons when they do not map to the same `when (subject)` behavior.
The `harness:terminal-branch-when` rule reports same-subject chains that end with `else` and use a stable function parameter.
The `harness:mid-function-exit` rule reports an unlabelled single exit before later block statements.
It checks either branch of an `if` used as a direct block statement.
Its supported exits are `return`, `break`, and `continue`.
It ignores terminal exits, labeled exits, lambda bodies, and value-producing `if` expressions.
Use condition inversion or `?.let` only when it preserves validation, failure, evaluation, and return semantics.
The `harness:nullable-elvis-return` rule reports lookup-backed property initializers whose Elvis fallback returns, including a bare `return`.
Use `?.let` for optional nullable work, and keep required failures explicit.

## Member Properties And Tests

Declare explicit types on every class, object, and companion object property, including private test helpers.
The `harness:explicit-property-type` rule applies to these members.
Give private top-level properties explicit types too.
Use Kotest specs for Kotlin tests and Kotest matchers for their assertions.
Choose the spec style for the test's structure.
Declare tests through the spec constructor DSL.
Keep shared immutable class-level test configuration in a typed companion object property.
Use the native ktlint rule engine for lint and formatting behavior.
Do not add suppressions to avoid declaring a member type.
