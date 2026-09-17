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
Use imports instead of fully qualified names in executable code.
Do not use wildcard imports.

## Names and bindings

Use lower camel case for methods, fields, parameters, and local variables.
Use upper camel case for types.
Prefer final fields and local bindings when mutation is not required.
Do not use `System.out` or `System.err` for application diagnostics; use the project's structured logger.

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
