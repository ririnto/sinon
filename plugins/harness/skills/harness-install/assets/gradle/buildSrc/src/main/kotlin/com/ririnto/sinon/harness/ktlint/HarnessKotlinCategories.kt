package com.ririnto.sinon.harness.ktlint

/**
 * Daemon-safe list of harness Kotlin rule category names.
 *
 * This object intentionally references no ktlint rule classes so that the Gradle daemon
 * (the plugin classloader, which carries ktlint only as `compileOnly`) can read the category
 * names without forcing the ktlint rule-engine classes onto its classpath. The ktlint rule
 * factories live in [HarnessKotlinRules], which is loaded only inside the worker's isolated
 * classloader where the ktlint engine is present. [HarnessKotlinRules] and this list MUST stay
 * in sync; a test asserts their equality.
 */
object HarnessKotlinCategories {
    /**
     * Harness Kotlin rule category names, in manifest-category order.
     */
    val categories: List<String> =
        listOf(
            "greaterThanComparison",
            "leafFunctionBlankLines",
            "implicitLambdaIt",
            "kotlinTopLevelDeclarationCount",
            "ifStatementBraces",
            "terminalBranchWhen",
            "nonNullAssertion",
            "uncheckedCastSuppression",
            "unstructuredLogging",
            "importOverFqn",
            "publicDeclarationDocComment",
            "leadingUnderscore",
            "multilineDocStyle",
            "companionObjectPosition",
        )
}
