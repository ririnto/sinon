package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.ririnto.sinon.ktlint.rule.CompanionObjectPosition
import com.ririnto.sinon.ktlint.rule.ComparisonDirection
import com.ririnto.sinon.ktlint.rule.ControlFlowBraces
import com.ririnto.sinon.ktlint.rule.DecorativeFunctionBodyBlankLines
import com.ririnto.sinon.ktlint.rule.ExplicitFunctionReturnType
import com.ririnto.sinon.ktlint.rule.ExplicitPropertyType
import com.ririnto.sinon.ktlint.rule.ImplicitLambdaIt
import com.ririnto.sinon.ktlint.rule.ImportOverFqn
import com.ririnto.sinon.ktlint.rule.KotlinTopLevelDeclarationCount
import com.ririnto.sinon.ktlint.rule.LeadingUnderscore
import com.ririnto.sinon.ktlint.rule.MultilineDocStyle
import com.ririnto.sinon.ktlint.rule.NestedDataClassLast
import com.ririnto.sinon.ktlint.rule.NoImportAlias
import com.ririnto.sinon.ktlint.rule.NonNullAssertion
import com.ririnto.sinon.ktlint.rule.PublicDeclarationDocComment
import com.ririnto.sinon.ktlint.rule.RegexConstructor
import com.ririnto.sinon.ktlint.rule.SlfDirectLogging
import com.ririnto.sinon.ktlint.rule.TerminalBranchWhen
import com.ririnto.sinon.ktlint.rule.UncheckedCastSuppression
import com.ririnto.sinon.ktlint.rule.UnstructuredLogging
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

/**
 * Provider for the custom ktlint rule set.
 */
class RuleSetProvider : RuleSetProviderV3(RuleSetId("code")) {
    override fun getRuleProviders(): Set<RuleProvider> =
        setOf(
            RuleProvider { ControlFlowBraces() },
            RuleProvider { ImportOverFqn() },
            RuleProvider { KotlinTopLevelDeclarationCount() },
            RuleProvider { ImplicitLambdaIt() },
            RuleProvider { LeadingUnderscore() },
            RuleProvider { UncheckedCastSuppression() },
            RuleProvider { NonNullAssertion() },
            RuleProvider { MultilineDocStyle() },
            RuleProvider { UnstructuredLogging() },
            RuleProvider { CompanionObjectPosition() },
            RuleProvider { ExplicitPropertyType() },
            RuleProvider { ComparisonDirection() },
            RuleProvider { TerminalBranchWhen() },
            RuleProvider { PublicDeclarationDocComment() },
            RuleProvider { SlfDirectLogging() },
            RuleProvider { RegexConstructor() },
            RuleProvider { DecorativeFunctionBodyBlankLines() },
            RuleProvider { ExplicitFunctionReturnType() },
            RuleProvider { NestedDataClassLast() },
            RuleProvider { NoImportAlias() }
        )
}
