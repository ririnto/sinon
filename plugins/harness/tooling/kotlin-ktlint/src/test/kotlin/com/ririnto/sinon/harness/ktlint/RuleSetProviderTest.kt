package com.ririnto.sinon.harness.ktlint

import kotlin.test.Test
import kotlin.test.assertEquals

class RuleSetProviderTest {
    @Test
    fun registersEveryHarnessRule() {
        assertEquals(
            setOf(
                "harness:companion-object-position",
                "harness:comparison-direction",
                "harness:control-flow-braces",
                "harness:explicit-function-return-type",
                "harness:explicit-property-type",
                "harness:explicit-unit-branch",
                "harness:function-body-blank-lines",
                "harness:implicit-lambda-it",
                "harness:import-over-fqn",
                "harness:kotlin-top-level-declaration-count",
                "harness:leading-underscore",
                "harness:multiline-kdoc",
                "harness:nested-data-class-last",
                "harness:no-import-alias",
                "harness:no-java-path-api",
                "harness:no-line-comment",
                "harness:non-null-assertion",
                "harness:nullable-elvis-return",
                "harness:public-declaration-doc-comment",
                "harness:no-regex-constructor",
                "harness:slf-direct-logging",
                "harness:terminal-branch-when",
                "harness:unchecked-cast-suppression",
                "harness:unstructured-logging"
            ),
            RuleSetProvider().getRuleProviders().map { provider -> provider.ruleId.value }.toSet()
        )
    }
}
