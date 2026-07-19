package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalBranchWhenKtlintRuleTest {
    private val ruleProvider = RuleProvider { TerminalBranchWhenKtlintRule() }

    @Test
    fun reportsOnlyOutermostIfElseChainWithFinalElse() {
        val source = """
            fun sample(first: Boolean, second: Boolean) {
                if (first) {
                    work()
                } else if (second) {
                    continueWork()
                } else {
                    finish()
                }
                if (first) work()
            }
        """.trimIndent() + "\n"

        val errors = lintRule(ruleProvider, source)

        assertEquals(1, errors.size)
        assertEquals(RuleId("code:terminal-branch-when"), errors.single().ruleId)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun ignoresIfWithoutFinalElse() {
        val source = """
            fun sample(first: Boolean, second: Boolean) {
                if (first) work()
                if (first) work() else if (second) continueWork()
            }
        """.trimIndent() + "\n"

        assertTrue(lintRule(ruleProvider, source).isEmpty())
    }
}
