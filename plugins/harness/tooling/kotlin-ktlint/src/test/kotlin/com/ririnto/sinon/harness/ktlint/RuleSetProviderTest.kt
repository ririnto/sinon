package com.ririnto.sinon.harness.ktlint

import kotlin.test.Test
import kotlin.test.assertEquals

class RuleSetProviderTest {
    @Test
    fun registersEveryHarnessRule() {
        assertEquals(
            setOf(
                "harness:control-flow-braces",
                "harness:function-body-blank-lines",
                "harness:function-body-comments",
                "harness:implicit-lambda-it",
                "harness:multiline-kdoc",
                "harness:public-declaration-doc-comment"
            ),
            RuleSetProvider().getRuleProviders().map { provider -> provider.ruleId.value }.toSet()
        )
    }
}
