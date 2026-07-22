package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImplicitLambdaItTest {
    private val ruleProvider: RuleProvider = RuleProvider { ImplicitLambdaIt() }

    @Test
    fun simpleImplicitItLambdaAutocorrectsToExplicitItParameter() {
        val source = "val len: (String) -> Int = { it.length }\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        val formatted = RuleTestSupport.formatRule(ruleProvider, source)
        assertEquals("val len: (String) -> Int = { it -> it.length }\n", formatted)
        assertEquals(formatted, RuleTestSupport.formatRule(ruleProvider, formatted))
        assertTrue(RuleTestSupport.lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun multiStatementLambdaAutocorrectsAndPreservesBody() {
        val source = """
            fun transform(items: List<String>) = items.map {
                val value = it.trim()
                value.uppercase()
            }
        """.trimIndent() + "\n"
        val formatted = RuleTestSupport.formatRule(ruleProvider, source)
        assertEquals(
            """
            fun transform(items: List<String>) = items.map {
                it ->
                val value = it.trim()
                value.uppercase()
            }
            """.trimIndent() + "\n",
            formatted
        )
        assertEquals(formatted, RuleTestSupport.formatRule(ruleProvider, formatted))
    }

    @Test
    fun itInsideStringTemplateIsPreservedAfterAutocorrect() {
        val formatted = RuleTestSupport.formatRule(ruleProvider, "fun render(items: List<String>) = items.joinToString { \"<\$it>\" }\n")
        assertEquals(
            "fun render(items: List<String>) = items.joinToString { it -> \"<\$it>\" }\n",
            formatted
        )
        assertEquals(formatted, RuleTestSupport.formatRule(ruleProvider, formatted))
    }

    @Test
    fun nestedLambdaShadowingItAutocorrectsIndependentlyPerLambda() {
        val source = """
            fun nested(values: List<List<Int>>) = values.map {
                it.flatMap { inner -> inner + it.size }
            }
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        val formatted = RuleTestSupport.formatRule(ruleProvider, source)
        assertEquals(
            """
            fun nested(values: List<List<Int>>) = values.map {
                it ->
                it.flatMap { inner -> inner + it.size }
            }
            """.trimIndent() + "\n",
            formatted
        )
        assertEquals(formatted, RuleTestSupport.formatRule(ruleProvider, formatted))
    }

    @Test
    fun lambdaWithExplicitParameterIsNotFlagged() {
        val sourceExplicitIt = "val id: (Int) -> Int = { it -> it }\n"
        val sourceNamed = "val square: (Int) -> Int = { x -> x * x }\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, sourceExplicitIt).isEmpty())
        assertTrue(RuleTestSupport.lintRule(ruleProvider, sourceNamed).isEmpty())
        assertEquals(sourceExplicitIt, RuleTestSupport.formatRule(ruleProvider, sourceExplicitIt))
        assertEquals(sourceNamed, RuleTestSupport.formatRule(ruleProvider, sourceNamed))
    }

    @Test
    fun alreadyExplicitItLambdaIsNoOp() {
        val canonical = "val len: (String) -> Int = { it -> it.length }\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, canonical).isEmpty())
        assertEquals(canonical, RuleTestSupport.formatRule(ruleProvider, canonical))
        assertFalse(RuleTestSupport.formatRule(ruleProvider, canonical).contains("it -> it ->"))
    }
}
