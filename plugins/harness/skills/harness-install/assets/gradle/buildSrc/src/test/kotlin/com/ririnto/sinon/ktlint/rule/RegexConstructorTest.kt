package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegexConstructorTest {
    private val ruleProvider: RuleProvider = RuleProvider { RegexConstructor() }

    @Test
    fun autocorrectsOnePositionalStringTemplateExpression() {
        val source = "val pattern = Regex(\"a+\")\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("val pattern = \"a+\".toRegex()\n", RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun autocorrectsRegexConstructorUsedAsReceiver() {
        val source = "val matched = Regex(\"a+\").matches(\"aaa\")\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "val matched = \"a+\".toRegex().matches(\"aaa\")\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun autocorrectsRegexConstructorUsedAsArgument() {
        val source = "val result = listOf(Regex(\"a+\"))\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "val result = listOf(\"a+\".toRegex())\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun leavesUnsafeConstructorShapesUnchanged() {
        val source = """
            fun build(pattern: String, option: RegexOption): Regex {
                val first = Regex(pattern)
                val second = Regex(pattern = "a+")
                val third = Regex("a+", option)
                return Regex(makePattern())
            }
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(4, errors.size)
        errors.forEach { lintError -> assertFalse(lintError.canBeAutoCorrected) }
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun qualifiedReceiverRegexIsSafe() {
        val source = "class Factory\nfun build(factory: Factory) = factory.Regex(\"a+\")\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }
}
