# Kotlin Rules

These rules extend the [common rules](../rules.md) for Kotlin files.
Read them together with that document.
The [Gradle tool reference](../tools/gradle.md) owns commands and configuration.

## Effective Visibility And Documentation Comments

Treat a declaration as public when it is visible outside the module after accounting for its enclosing structure: a declaration inside an `internal` or `private` scope is no wider than that scope.
Every effective public or protected declaration carries a KDoc comment.
Write KDoc in English in the multiline form: an opening `/**` line, meaningful English sentences on lines that begin with an asterisk, and a closing `*/` line.
Concise KDoc states the contract, not a restatement of the name.
A purely private helper needs no KDoc.

## Function Bodies And Comments

Prefer an expression body for a function that consists of a single returned expression when the return type, `Unit` behavior, nullability, and API semantics stay unchanged.
Split a combined pure transformation into readable chain steps, and use callable references where they fit, for example `map { line -> line.substringBefore('#') }`, `map(String::trim)`, `filterNot(String::isEmpty)`, `mapNotNull(String::toLongOrNull)`, `map(::UserId)`, `take(500)`, `toList()`.
Keep an explicit named lambda where an extra bound argument is required.
Do not split operations indiscriminately when it would change eager evaluation, side effects, exceptions, or performance assumptions relevant to the contract.
For optional nullable-value processing, prefer `?.let` with a named non-null parameter over an intermediate safe variable and an early return, and use the captured parameter instead of rereading the outer nullable variable or property.
This preference does not turn validation failure into silently skipped processing and does not forbid guard clauses serving a different contract.
Put braces around every `if` and `else` branch, including expression branches, guard returns, and one-line branches.
Write function and constructor argument lists without a trailing comma.

## Registered Components And Required Values

Treat a class that a container or DI framework constructs and registers as a bean.
Declare every constructor parameter that receives a dependency or a configuration value non-null and without a default value.
A required value missing at registration is a startup failure, not a nullable property or a silent code fallback.
Configuration values belong to the configuration source, not to code defaults: in Spring Boot, a `@ConfigurationProperties` class declares the bound parameters and `application.yaml` supplies their values.
Express genuinely optional behavior as explicit strategy implementations selected at composition time instead of a nullable or defaulted dependency.

## Bindings And Lambdas

Use `val` for values that do not need reassignment; the common rules govern the conversion.
Prefer a function reference over a pass-through lambda when the reference is equivalent in meaning, type, overload resolution, and receiver binding.
Name every lambda parameter explicitly for its role instead of the implicit `it`, with no exception for short lambdas.
Use `_` only for a genuinely unused parameter.
Do not confuse the Kotest DSL form `it("description")` with an implicit lambda parameter.
Use extension functions when ownership or the call site benefits.
Do not add factories, DSLs, or layers for appearance.

## Raw Strings

Use raw strings (`"""`) for regular expressions and JSON fixtures.
Know whether the trailing newline before the closing delimiter is part of the value, and match the exact target.

## KtLint Suppression Placement

For the inferred `assertThatRule` test helper property, ktlint 1.8.0 does not honor a class-level `@Suppress` placed after the `package` directive.
The test sources use `@file:Suppress("ktlint:harness:explicit-property-type")` before the `package` line as the working module-level form.
