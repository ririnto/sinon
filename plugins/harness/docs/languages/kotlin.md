---
title: Kotlin Rules
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
    Kotlin Collection Builders:
      url: https://kotlinlang.org/docs/constructing-collections.html
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

These rules extend the [common rules](../rules.md) for Kotlin files.
Read them together with that document.
Load this reference only after reading the Harness `implement` or `review` skill.
The [Gradle tool reference](../tools/gradle.md) owns commands and configuration.

## Effective Visibility And Documentation Comments

Treat a declaration as public when it is visible outside its module.
Account for enclosing scopes.
An `internal` or `private` scope limits declarations inside it.
Document effective public or protected types and callable contracts with KDoc.
Document properties when their meaning or constraints are not evident from the declaration.
Describe parameters, results, errors, and links when they clarify the contract.
Write KDoc in English with multiple lines.
Put the opening `/**` and closing `*/` delimiters on separate lines, including for one-sentence KDoc.
Write meaningful English sentences on lines beginning with `*`.
Use ASCII tree markers in KDoc instead of Unicode box-drawing characters.
The `harness:multiline-kdoc` rule reports the first box-drawing character on each KDoc line without an automatic fix.
Concise KDoc states the contract, not a restatement of the name.
A purely private helper needs no KDoc.
The `harness:public-declaration-doc-comment` rule is on by default and reports effective public classes, interfaces, named objects, and functions without KDoc.
It skips `override` functions.
It does not require a separate comment on properties or primary-constructor properties.

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
Prefer `takeUnless { condition }` to `takeIf { !condition }` only when Boolean behavior is identical.
Preserve evaluation order and count, short-circuiting, effects, and exception timing.
Confine local mutable accumulation that constructs a collection result to `buildList`, `buildSet`, and `buildMap` scopes.
This requirement excludes owned long-lived mutable state, stateful delegates, recursive visited sets, and algorithmic worklists.
Do not let a mutable builder receiver escape its lambda.
Use a `Sequence` only when lazy evaluation matches the contract.
For optional nullable processing, prefer `?.let` with a named non-null parameter.
Use the captured parameter instead of rereading the nullable property.
Do not replace required validation or failure behavior with silently skipped processing.
Keep terminal returns when they express the function's result.
Move a loop exit into the loop condition when behavior and evaluation order remain unchanged.
Put braces around every `if` and `else` branch, including expression branches, guard returns, and one-line branches.
Write function and constructor argument lists without a trailing comma.
Keep short calls on one line when they fit the configured line limit.
The `harness:function-body-blank-lines` rule removes decorative blank lines inside function, lambda, initializer, accessor, and secondary-constructor blocks at every nesting depth.
It keeps the separation around local functions, local classes, annotations, and comments.
It leaves Kotest registration scopes to `harness:kotest-sibling-spacing`.
The `harness:no-line-comment` rule reports ordinary `//` and `/* */` comment tokens at every source placement.
KDoc remains outside its token set and is checked by ktlint's standard `kdoc` rule and Harness declaration-documentation rules.
Shebangs also remain outside this rule's token set.
The rule preserves recognized formatter, lint-suppression, and license directives without an automatic fix.
Other comment placement remains subject to the common declaration-documentation policy.
Put rationale in KDoc on the declaration instead.
The `harness:unstructured-logging` rule reports `print`, `println`, `System.out` and `System.err` print calls, and `printStackTrace`.
It skips console functions and stream roots shadowed by lexical declarations or conflicting imports.
The `harness:slf-direct-logging` rule reports `trace`, `debug`, `info`, `warn`, and `error` calls only on receivers that resolve lexically to an SLF4J logger.
A same-named local, parameter, or member that is not a logger is never reported.

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
The `harness:non-null-assertion` rule reports every `!!` and never rewrites it into a guard, because `requireNotNull` throws `IllegalArgumentException` where `!!` throws `NullPointerException`.
The rule is diagnostic-only because syntax cannot prove a guard call's identity or non-null return type.
Inherited declarations and lexical shadowing can change even apparently standard-library calls.
In production code, prefer `requireNotNull` for required arguments and `checkNotNull` for required state over Boolean null preconditions.
Keep the original exception type, message, evaluation order, wrapper contract, and `Unit` return type for production guards.
`requireNotNull` throws `IllegalArgumentException`, while `checkNotNull` throws `IllegalStateException` for null values.

Name every lambda parameter explicitly for its role instead of the implicit `it`, with no exception for short lambdas.
Use `_` only for a genuinely unused parameter.
Do not confuse the Kotest DSL form `it("description")` with an implicit lambda parameter.
Use extension functions when ownership or the call site benefits.
Do not add factories, DSLs, or layers for appearance.

## Test Assertions

In Kotlin test sources, use Kotest matchers for null and Boolean assertions.
The `harness:kotest-test-assertions` rule reports standard-library `requireNotNull` and `checkNotNull` calls, including imports under aliases.
It reports `require` and `check` calls and asks for `shouldBeTrue()` on the asserted condition.
It also reports Kotlin standard-library `assert` calls and asks for `shouldBeTrue()` on the asserted condition.
It reports imported `kotlin.test.assertTrue` and `assertFalse` calls and asks for `shouldBeTrue()` or `shouldBeFalse()` on the asserted condition.
It reports imported `kotlin.test.assertNotNull` calls and asks for `shouldNotBeNull()` for null assertions.
The Kotlin test assertion detection supports explicit imports, aliases, and wildcard imports.
Unqualified imported assertions inside classes with unresolved supertypes are left unchanged because an inherited member may take precedence.
It reports a safe-call expression when an imported Kotest matcher directly asserts its value.
The matcher detection supports direct imports, aliases, and wildcard imports for Kotest `should` matchers.
The rule recognizes test source sets immediately below `src`, including `test`, `androidTest`, `commonTest`, source sets beginning with `test`, and source sets ending in `Test` or `Tests`.
Use `shouldNotBeNull()` before assertions that require a nullable value to be present.
These diagnostics have no automatic fixes because changing assertion calls can change exceptions, evaluation, and failure behavior.
Production source paths remain unaffected.

## Imports

Prefer a class import when its simple name is unambiguous.
Keep a fully qualified name when importing it would collide with a declaration, another imported type, or an alias.
Wildcard imports are permitted.
The Kotlin profile disables ktlint's standard wildcard-import ban to honor this repository rule.
Meaningful Kotlin aliases remain available when two imported declarations share a simple name.
The `harness:import-over-fqn` rule examines fully qualified type references only and reports candidates with one import path for their simple name and no conflicting declaration, import, alias, wildcard import, same-package reference, or existing unqualified use.
The rule uses the current file's syntax and imports without compiler symbol resolution, so it leaves expression chains unchanged because a lowercase receiver can resolve to a local or imported value.
The `harness:no-import-alias` rule rejects only aliases that repeat the imported simple name.

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
Follow the [common early-exit policy](../rules.md#code-rules-common-to-all-languages).

## Member Properties And Tests

Declare explicit types on every class, object, and companion object property, including private test helpers.
The `harness:explicit-property-type` rule applies to these members.
The rule also reports top-level properties, whatever their visibility.
Local variables keep inference.
It fixes literal initializers automatically and reports other initializers without a correction.
The `harness:kotlin-top-level-declaration-count` rule requires exactly one top-level class, object, interface, or type alias in each non-script file.
It does not count empty files, private top-level functions, properties, and type aliases, or a single top-level `fun main`.
Use Kotest specs for Kotlin tests and Kotest matchers for their assertions.
Choose the spec style for the test's structure.
Declare tests through the spec constructor DSL.
Separate sibling tests and lifecycle hooks with exactly one blank line.
The `harness:kotest-sibling-spacing` rule enforces this only where the registration scope is proven.
That scope is a `StringSpec` or `FunSpec` imported by its Kotest name or alias, its constructor lambda or `init` block, and nested `context` blocks.
It leaves star imports, shadowed `test`, `context`, hook, `invoke`, and `config` names, same-line registrations, and other statements unchanged.
Keep shared immutable class-level test configuration in a typed companion object property.
Use the native ktlint rule engine for lint and formatting behavior.
Do not add suppressions to avoid declaring a member type.
