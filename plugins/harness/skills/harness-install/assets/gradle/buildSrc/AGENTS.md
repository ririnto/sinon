# Custom Ktlint Rules

This module provides the custom ktlint rules registered by `RuleSetProvider`.

## Project Structure

`src/main/kotlin/com/ririnto/sinon/ktlint/RuleSetProvider.kt` registers every custom rule.
`src/main/kotlin/com/ririnto/sinon/ktlint/rule/` contains one rule implementation per Kotlin file.
`src/test/kotlin/com/ririnto/sinon/ktlint/RuleSetProviderTest.kt` checks the registered rule identifiers.
`src/test/kotlin/com/ririnto/sinon/ktlint/rule/RuleTestSupport.kt` provides isolated lint and format helpers.
`src/test/kotlin/com/ririnto/sinon/ktlint/rule/` contains focused tests beside the rule inventory.
`build.gradle.kts` defines Kotlin, ktlint, and JUnit 5 dependencies and test execution.

### Local Decisions

`RuleSetProvider` plus `RuleSetProviderTest` are the executable inventory source of truth.
Prose must not duplicate a standalone rule count.
`RuleSetProviderTest` must maintain the complete expected rule ID set.
Rule identifiers use the `code:<kebab-case-name>` form.
Register `ExplicitUnitBranch` as lint-only and do not give it autocorrection.
Rules that depend on editor configuration remain opt-in through explicit test configuration.
Use camelCase test names that describe one observable behavior.
Expression, statement, naming, and member rules inspect both `.kt` and `.kts` inputs.
`KotlinTopLevelDeclarationCount` and `PublicDeclarationDocComment` skip scripts.

## Build, Test, and Development Commands

From the installed target root, run `./gradlew -p buildSrc test` for module tests.
From the installed target root, run `./gradlew -p buildSrc test --tests "com.ririnto.sinon.ktlint.RuleSetProviderTest"` for registration coverage.

## Coding Style and Testing

Prefer typed PSI nodes and properties over raw source text matching.
Keep syntax-only analysis inside PSI boundaries and do not infer types, symbols, or cross-file meaning.
Use `===` and `!==` only for null checks and enum identity checks.
Prefer `is` checks with smart casts when a stable local can be narrowed.
Retain `as?` at genuinely nullable PSI boundaries.
Inline single-use locals unless a local is needed for a stable smart cast.
Do not use mid-function returns or `?: return` patterns.
Apply De Morgan transformations instead of negated conjunctions or disjunctions.
Name every lambda parameter and do not use implicit lambda `it`.
Visitor callback accumulators may use `mutableListOf`.
Apply autocorrect only inside explicit approval gates.
Keep autocorrect output exact and idempotent.

### Rule Coverage

Control flow and declaration rules enforce braces, branch structure, explicit return and property types, and top-level declaration limits.
Naming and syntax rules check leading underscores, implicit lambda names, import aliases, regex construction, and comparison direction.
Documentation and layout rules check public declaration documentation, multiline documentation style, companion object position, and decorative blank lines.
Safety and logging rules check non-null assertions, unchecked cast suppression, direct SLF logging, and unstructured logging.
Import and declaration rules prefer imports over fully qualified names and keep nested data classes in the expected position.

### Testing

Give each rule a dedicated test class unless a tightly related rule has an established shared fixture.
Test one behavior per test with a minimal Kotlin snippet.
Assert exact lint messages, offsets, and error counts when those details define the contract.
Test valid examples as well as violations and boundary syntax.
Use `RuleTestSupport.lintRule` for diagnostics and `RuleTestSupport.formatRule` for autocorrect.
For autocorrect, assert the exact formatted output and run the result through formatting again to prove idempotence.

## Security and Configuration

### Known Limitations

Logger shadowing can make logger detection ambiguous because analysis is syntax-only.
Cross-file null-guard shadowing is not resolved without semantic analysis.
Built-in type collisions can make a name look like a standard Kotlin type when it is user-defined.
Fully qualified name checks cannot distinguish package and value ambiguity reliably.
Unqualified `Unit` shadowing can produce false positives for explicit unit branch checks.
Do not add credential access, generated output, or alternate rule configuration to compensate for these limits.
