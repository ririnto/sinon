package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlFlowBracesTest {
    private val ruleProvider: RuleProvider = RuleProvider { ControlFlowBraces() }

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
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
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
            RuleTestSupport.formatRule(ruleProvider, source)
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
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun formatsNestedUnbracedControlFlow() {
        val source = """
            fun sample(first: Boolean, second: Boolean, third: Boolean) {
                if (first) {
                    if (second) work()
                } else {
                    if (third) stop()
                }
            }
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(2, errors.size)
        errors.forEach { lintError ->
            assertEquals(RuleId("code:control-flow-braces"), lintError.ruleId)
            assertTrue(lintError.canBeAutoCorrected)
        }
        assertEquals(
            """
            fun sample(first: Boolean, second: Boolean, third: Boolean) {
                if (first) {
                    if (second) {
                        work()
                    }
                } else {
                    if (third) {
                        stop()
                    }
                }
            }
            """.trimIndent() + "\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun leavesMultilineRawStringBodyLintOnly() {
        val source = "fun sample(ready: Boolean) {\n" +
            "    if (ready) println(\"\"\"\n" +
            "value\n" +
            "\"\"\")\n" +
            "}\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }
}
