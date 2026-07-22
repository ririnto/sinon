package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnstructuredLoggingTest {
    private val ruleProvider: RuleProvider = RuleProvider { UnstructuredLogging() }

    @Test
    fun printlnIsFlagged() {
        val errors = RuleTestSupport.lintRule(ruleProvider, "fun log() = println(\"message\")\n")
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun qualifiedPrintlnIsFlagged() {
        assertEquals(1, RuleTestSupport.lintRule(ruleProvider, "fun log() = kotlin.io.println(\"message\")\n").size)
    }

    @Test
    fun multiplePrintlnCallsAreAllFlagged() {
        val errors = RuleTestSupport.lintRule(
            ruleProvider,
            """
            fun log() {
                println("one")
                kotlin.io.println("two")
            }
            """.trimIndent() + "\n"
        )
        assertEquals(2, errors.size)
        assertTrue(errors.all { error -> !error.canBeAutoCorrected })
    }

    @Test
    fun receiverPrintlnIsSafe() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "class Logger { fun println(msg: String) {} }\nfun log(l: Logger) = l.println(\"message\")\n").isEmpty())
    }

    @Test
    fun nonKotlinCalleeIsSafe() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "fun log(error: Throwable) = error.printStackTrace()\n").isEmpty())
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "fun log() = print(\"message\")\n").isEmpty())
    }

    @Test
    fun safeAccessAndNotNullAssertionReceiversAreSafe() {
        val source = """
            class Logger {
                fun println(message: String) {}
            }

            fun log(foo: Logger?) {
                foo?.println("msg")
                foo!!.println("msg")
            }
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
    }
}
