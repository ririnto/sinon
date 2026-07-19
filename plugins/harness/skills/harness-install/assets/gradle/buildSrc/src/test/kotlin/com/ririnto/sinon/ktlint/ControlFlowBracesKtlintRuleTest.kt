package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlFlowBracesKtlintRuleTest {
    private val ruleProvider = com.pinterest.ktlint.rule.engine.core.api.RuleProvider { ControlFlowBracesKtlintRule() }

    @Test
    fun diagnosesAndFormatsEveryUnbracedControlFlowBody() {
        val source = """
            fun sample(items: List<Int>, ready: Boolean) {
                if (ready) work()
                else stop()
                for (item in items) use(item)
                while (ready) tick()
                do tick() while (ready)
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(5, errors.size)
        errors.forEach { lintError ->
            assertEquals(RuleId("code:control-flow-braces"), lintError.ruleId)
            assertTrue(lintError.canBeAutoCorrected)
        }
        assertEquals(
            """
                fun sample(items: List<Int>, ready: Boolean) {
                    if (ready) {
                        work()
                    }
                    else {
                        stop()
                    }
                    for (item in items) {
                        use(item)
                    }
                    while (ready) {
                        tick()
                    }
                    do {
                        tick()
                    } while (ready)
                }
            """.trimIndent() + "\n",
            formatRule(ruleProvider, source)
        )
    }

    @Test
    fun leavesBracedBodiesUnchangedAfterAutocorrect() {
        val source = """
            fun sample(ready: Boolean) {
                if (ready) {
                    work()
                }
                while (ready) {
                    tick()
                }
            }
        """.trimIndent() + "\n"
        assertTrue(lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, formatRule(ruleProvider, source))
        assertFalse(formatRule(ruleProvider, formatRule(ruleProvider, source)).contains("wrap the"))
    }
}
