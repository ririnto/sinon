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
            RuleProvider { ControlFlowBraces() },
            RuleProvider { FunctionBodyBlankLines() },
            RuleProvider { FunctionBodyComments() },
            RuleProvider { ImplicitLambdaIt() },
            RuleProvider { MultilineKdoc() },
            RuleProvider { PublicDeclarationDocComment() }
        )
}
