package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.RuleSetId

/**
 * Provides the Harness Kotlin rules to ktlint through ServiceLoader discovery.
 */
class RuleSetProvider : RuleSetProviderV3(RuleSetId("harness")) {
    override fun getRuleProviders(): Set<RuleProvider> =
        setOf(
            RuleProvider { CompanionObjectPosition() },
            RuleProvider { ComparisonDirection() },
            RuleProvider { ControlFlowBraces() },
            RuleProvider { ExplicitFunctionReturnType() },
            RuleProvider { ExplicitPropertyType() },
            RuleProvider { ExplicitUnitBranch() },
            RuleProvider { FunctionBodyBlankLines() },
            RuleProvider { ImplicitLambdaIt() },
            RuleProvider { ImportOverFqn() },
            RuleProvider { KotlinTopLevelDeclarationCount() },
            RuleProvider { LeadingUnderscore() },
            RuleProvider { MultilineKdoc() },
            RuleProvider { NestedDataClassLast() },
            RuleProvider { NoImportAlias() },
            RuleProvider { NoJavaPathApi() },
            RuleProvider { NoLineComment() },
            RuleProvider { NonNullAssertion() },
            RuleProvider { NullableElvisReturn() },
            RuleProvider { PublicDeclarationDocComment() },
            RuleProvider { RegexConstructor() },
            RuleProvider { SlfDirectLogging() },
            RuleProvider { TerminalBranchWhen() },
            RuleProvider { UncheckedCastSuppression() },
            RuleProvider { UnstructuredLogging() }
        )
}
