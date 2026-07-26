package com.ririnto.sinon.ktlint

import kotlin.test.Test
import kotlin.test.assertEquals

class RuleSetProviderTest {
    @Test
    fun providesEveryCustomRule() {
        assertEquals(
            setOf(
                "code:comparison-direction",
                "code:companion-object-position",
                "code:control-flow-braces",
                "code:explicit-function-return-type",
                "code:explicit-property-type",
                "code:explicit-unit-branch",
                "code:implicit-lambda-it",
                "code:import-over-fqn",
                "code:leading-underscore",
                "code:multiline-doc-style",
                "code:no-import-alias",
                "code:no-decorative-function-body-blank-lines",
                "code:no-line-comment",
                "code:no-regex-constructor",
                "code:non-null-assertion",
                "code:public-declaration-doc-comment",
                "code:slf-direct-logging",
                "code:terminal-branch-when",
                "code:unchecked-cast-suppression",
                "code:unstructured-logging",
                "code:kotlin-top-level-declaration-count",
                "code:nested-data-class-last"
            ),
            RuleSetProvider()
                .getRuleProviders()
                .map { ruleProvider -> ruleProvider.ruleId.value }
                .toSet()
        )
    }
}
