package com.ririnto.sinon.harness.ktlint

import com.ririnto.sinon.harness.core.RuleContext

/**
 * Central registry of every harness Kotlin rule and its ktlint rule constructor.
 *
 * This registry is the single source of truth that maps a manifest category to the ktlint
 * rule that enforces it. New Kotlin rules are added here once their rule class exists.
 */
object HarnessKotlinRules {
    /**
     * All harness Kotlin rule specifications, in manifest-category order.
     */
    val specs: List<RuleSpec> =
        listOf(
            RuleSpec("leafFunctionBlankLines") { ctx -> LeafFunctionBlankLinesKtlintRule(ctx) },
            RuleSpec("implicitLambdaIt") { ctx -> ImplicitLambdaItKtlintRule(ctx) },
            RuleSpec("kotlinTopLevelDeclarationCount") { ctx -> KotlinTopLevelDeclarationCountKtlintRule(ctx) },
            RuleSpec("ifStatementBraces") { ctx -> IfStatementBracesKtlintRule(ctx) },
            RuleSpec("terminalBranchWhen") { ctx -> TerminalBranchWhenKtlintRule(ctx) },
            RuleSpec("nonNullAssertion") { ctx -> NonNullAssertionKtlintRule(ctx) },
            RuleSpec("uncheckedCastSuppression") { ctx -> UncheckedCastSuppressionKtlintRule(ctx) },
            RuleSpec("unstructuredLogging") { ctx -> UnstructuredLoggingKtlintRule(ctx) },
            RuleSpec("importOverFqn") { ctx -> ImportOverFqnKtlintRule(ctx) },
            RuleSpec("publicDeclarationDocComment") { ctx -> PublicDeclarationDocCommentKtlintRule(ctx) },
            RuleSpec("leadingUnderscore") { ctx -> LeadingUnderscoreKtlintRule(ctx) },
            RuleSpec("multilineDocStyle") { ctx -> MultilineDocStyleKtlintRule(ctx) },
            RuleSpec("companionObjectPosition") { ctx -> CompanionObjectPositionKtlintRule(ctx) },
        )

    /**
     * Pairs a manifest category with the factory that builds its ktlint rule for a context.
     */
    data class RuleSpec(
        val category: String,
        val create: (RuleContext) -> HarnessKtlintRule,
    )
}
