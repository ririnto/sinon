---
metadata:
  reference:
    Java language:
      version: JDK 25
      url:
        - https://docs.oracle.com/en/java/javase/25/language/java-language-changes.html
---

# Java

Use the target build's declared Java toolchain and release as the compatibility boundary.
Keep source changes compatible with that release unless the project explicitly upgrades it.
The [shared rules](../rules.md) apply alongside these Java-only invariants.
The [Gradle tool reference](../tools/gradle.md) and the [Maven tool reference](../tools/maven.md) own commands and configuration.

## Public declarations

Document public classes, interfaces, enums, annotations, methods, constructors, and fields with Javadoc.
Write declaration documentation in English and state the contract, constraints, or reason.
Keep documentation at declaration level instead of placing explanatory comments inside method bodies.

## Structure

Use braces for all control-flow blocks, including one-line branches.
Keep methods focused on one responsibility and keep boundary parsing separate from domain logic.
Prefer explicit imports over fully qualified names when the imported type is unambiguous.
Keep a fully qualified name when an import would collide with another type or declaration.
Wildcard imports are permitted.

## Names and bindings

Use lower camel case for methods, fields, parameters, and local variables.
Use upper camel case for types.
Prefer final fields and local bindings when mutation is not required.
Do not use `System.out` or `System.err` for application diagnostics.
Use the project's structured logger.

## Formatting and validation

Use the selected build tool's native formatter and static checker.
Run the tool's format check and the project's normal test or verification lifecycle.
Preserve existing package names, module names, coordinates, source sets, toolchains, and build tasks.
Do not replace an existing formatter or checker without reviewing its current ownership and scope.

## Version-sensitive syntax

Name the Java LTS release before introducing release-specific syntax.
Prefer stable syntax over preview features.
Use records, sealed types, pattern matching, and switch expressions only when the declared release supports them.
Do not treat withdrawn string templates as a Java language feature.

## Kotlin Ruleset Parity

The Java profile maps rules to Java syntax and uses Checkstyle with Spotless.
`tooling/java/.editorconfig` preserves UTF-8, LF endings, four-space indentation, a final newline, and trimmed trailing whitespace.
Spotless supplies Palantir Java Format and removes unused imports; its Java layout is not a byte-for-byte port of ktlint's Kotlin style.
Checkstyle enforces braces, method and constructor separation, public and protected Javadoc presence, naming, line length, and array or enum comma rules.
The mappings below record where Java has a safe language or tool equivalent.

| Kotlin rule | Java profile behavior |
| --- | --- |
| `ktlint_official`, `KOTLIN_STYLE_GUIDE`, shared `.editorconfig` settings | Java uses Palantir Java Format plus the shared UTF-8, LF, four-space, final-newline, and trim-trailing-whitespace settings. The language formatter choices are not byte-for-byte equivalent. |
| `if-else-bracing`, `control-flow-braces` | Checkstyle `NeedBraces` requires braces for Java control-flow blocks. |
| `no-consecutive-blank-lines` | Checkstyle `EmptyLineSeparator` disallows repeated blank lines around methods and constructors and inside class members. It does not match every Kotlin whitespace case. |
| `trailing-comma-on-call-site`, `trailing-comma-on-declaration-site` | Java does not allow trailing commas in calls or parameter lists; Checkstyle also rejects them in array initializers and enum constant lists. |
| `no-wildcard-imports` | The Kotlin profile disables this rule because wildcard imports are permitted in both languages. |
| `function-expression-body`, `multiline-if-else` | Java has no Kotlin expression-body declaration, and Palantir formats Java branches according to its Java style rather than ktlint's Kotlin-specific layout. |
| `explicit-function-return-type`, `explicit-property-type` | Java requires declared return types and field types in these positions; local `var` remains valid. |
| `public-declaration-doc-comment`, `multiline-kdoc` | Checkstyle requires Javadoc on public and protected types, methods, constructors, and fields, but does not enforce multiline delimiters or English contract quality. |
| `leading-underscore` | Checkstyle name checks enforce lower camel case for Java bindings, upper camel case for types and type parameters, and standard camel or upper-snake case for constants; `_` remains allowed for unnamed lambda, local, catch, pattern, or parameter bindings. Overridden method names and parameters are exempt, but Checkstyle has no safe built-in equivalent for Kotlin's additional exemption for `open`, `abstract`, or interface declarations. |
| `import-over-fqn` | No Checkstyle or Spotless rule is selected because they cannot prove that an import is collision-free across source files and wildcard imports. Follow the Java import guidance above and keep a qualified name when import safety is uncertain. |
| `unstructured-logging` | The existing Checkstyle pattern flags line-leading `System.out.print*` and `System.err.print*` calls. It is lexical, so it cannot distinguish shadowed names or matching text-block content. |
| `function-body-blank-lines` | The common rule against decorative blank lines inside function bodies remains a review rule because the selected Checkstyle checks do not inspect method-body whitespace safely. |
| `no-line-comment` | No direct Checkstyle equivalent is selected; Java documentation stays at declarations, and comment placement remains a review rule. |
| `comparison-direction`, `mid-function-exit`, `terminal-branch-when` | No Java checker is selected because syntax-only rewrites cannot reliably preserve operand evaluation order, equality behavior, or control flow. |
| `unchecked-cast-suppression` | No Java checker is selected for `@SuppressWarnings("unchecked")`; the repository rule against adding suppressions remains in force. |
| `implicit-lambda-it` | Java requires an explicit lambda parameter name in source syntax. |
| `kotlin-top-level-declaration-count` | The Java compiler requires a public top-level type to match its file name, but allows additional package-private top-level types. There is no exact Checkstyle equivalent for Kotlin's one-type rule. |
| `companion-object-position`, `explicit-unit-branch`, `nested-data-class-last`, `no-import-alias`, `no-java-path-api`, `non-null-assertion`, `null-comparison-identity`, `nullable-elvis-return`, `no-regex-constructor`, `slf-direct-logging` | These rules depend on Kotlin syntax, Kotlin null semantics, Kotlin-specific constructs, or Kotlin APIs and have no direct Java equivalent. Java null comparisons already use reference identity. |

The Java profile permits wildcard imports.
The Kotlin ruleset also permits wildcard imports, checks fully qualified type references only, and skips candidates with colliding names, conflicting aliases, same-package references, existing unqualified uses, or any wildcard import.
