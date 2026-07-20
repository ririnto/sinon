package com.ririnto.sinon.ktlint.rule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegexConstructorTest {
    private val ruleProvider = com.pinterest.ktlint.rule.engine.core.api.RuleProvider { RegexConstructor() }

    @Test
    fun autocorrectsOnePositionalStringTemplateExpression() {
        val source = "val pattern = Regex(\"a+\")\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("val pattern = \"a+\".toRegex()\n", formatRule(ruleProvider, source))
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
        val errors = lintRule(ruleProvider, source)
        assertEquals(4, errors.size)
        errors.forEach { lintError -> assertFalse(lintError.canBeAutoCorrected) }
        assertEquals(source, formatRule(ruleProvider, source))
    }
}
