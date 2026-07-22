package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalBranchWhenTest {
    private val ruleProvider: RuleProvider = RuleProvider { TerminalBranchWhen() }

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
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertEquals(RuleId("code:terminal-branch-when"), errors.single().ruleId)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun ignoresIfWithoutFinalElse() {
        assertTrue(
            RuleTestSupport.lintRule(
                ruleProvider,
                """
                    fun sample(first: Boolean, second: Boolean) {
                        if (first) work()
                        if (first) work() else if (second) continueWork()
                    }
                """.trimIndent() + "\n"
            ).isEmpty()
        )
    }
}
