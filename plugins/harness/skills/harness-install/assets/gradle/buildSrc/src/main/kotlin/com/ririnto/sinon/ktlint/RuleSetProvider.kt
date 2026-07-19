package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

/**
 * Provider for the custom ktlint rule set.
 */
class RuleSetProvider : RuleSetProviderV3(RuleSetId("code")) {
    override fun getRuleProviders(): Set<RuleProvider> =
        setOf(
            RuleProvider { ControlFlowBracesKtlintRule() },
            RuleProvider { ImportOverFqnKtlintRule() },
            RuleProvider { KotlinTopLevelDeclarationCountKtlintRule() },
            RuleProvider { ImplicitLambdaItKtlintRule() },
            RuleProvider { LeadingUnderscoreKtlintRule() },
            RuleProvider { UncheckedCastSuppressionKtlintRule() },
            RuleProvider { NonNullAssertionKtlintRule() },
            RuleProvider { MultilineDocStyleKtlintRule() },
            RuleProvider { UnstructuredLoggingKtlintRule() },
            RuleProvider { CompanionObjectPositionKtlintRule() },
            RuleProvider { ExplicitPropertyTypeKtlintRule() },
            RuleProvider { ComparisonDirectionKtlintRule() },
            RuleProvider { TerminalBranchWhenKtlintRule() },
            RuleProvider { PublicDeclarationDocCommentKtlintRule() },
            RuleProvider { SlfDirectLoggingKtlintRule() },
            RuleProvider { RegexConstructorKtlintRule() },
            RuleProvider { DecorativeFunctionBodyBlankLinesKtlintRule() },
            RuleProvider { ExplicitFunctionReturnTypeKtlintRule() },
            RuleProvider { NullableElvisReturnKtlintRule() },
            RuleProvider { NestedDataClassLastKtlintRule() }
        )
}
