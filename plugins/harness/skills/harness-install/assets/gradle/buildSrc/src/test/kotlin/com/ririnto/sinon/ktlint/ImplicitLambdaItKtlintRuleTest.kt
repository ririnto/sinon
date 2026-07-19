package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImplicitLambdaItKtlintRuleTest {
    private val ruleProvider = RuleProvider { ImplicitLambdaItKtlintRule() }

    @Test
    fun simpleImplicitItLambdaAutocorrectsToExplicitItParameter() {
        val source = "val len: (String) -> Int = { it.length }\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        val formatted = formatRule(ruleProvider, source)
        assertEquals("val len: (String) -> Int = { it -> it.length }\n", formatted)
        assertEquals(formatted, formatRule(ruleProvider, formatted))
        assertTrue(lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun multiStatementLambdaAutocorrectsAndPreservesBody() {
        val source = """
            fun transform(items: List<String>) = items.map {
                val value = it.trim()
                value.uppercase()
            }
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
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
        assertEquals(formatted, formatRule(ruleProvider, formatted))
    }

    @Test
    fun itInsideStringTemplateIsPreservedAfterAutocorrect() {
        val source = "fun render(items: List<String>) = items.joinToString { \"<\$it>\" }\n"
        val formatted = formatRule(ruleProvider, source)
        assertEquals(
            "fun render(items: List<String>) = items.joinToString { it -> \"<\$it>\" }\n",
            formatted
        )
        assertEquals(formatted, formatRule(ruleProvider, formatted))
    }

    @Test
    fun nestedLambdaShadowingItAutocorrectsIndependentlyPerLambda() {
        val source = """
            fun nested(values: List<List<Int>>) = values.map {
                it.flatMap { inner -> inner + it.size }
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        val formatted = formatRule(ruleProvider, source)
        assertEquals(
            """
            fun nested(values: List<List<Int>>) = values.map {
                it ->
                it.flatMap { inner -> inner + it.size }
            }
            """.trimIndent() + "\n",
            formatted
        )
        assertEquals(formatted, formatRule(ruleProvider, formatted))
    }

    @Test
    fun lambdaWithExplicitParameterIsNotFlagged() {
        val sourceExplicitIt = "val id: (Int) -> Int = { it -> it }\n"
        val sourceNamed = "val square: (Int) -> Int = { x -> x * x }\n"
        assertTrue(lintRule(ruleProvider, sourceExplicitIt).isEmpty())
        assertTrue(lintRule(ruleProvider, sourceNamed).isEmpty())
        assertEquals(sourceExplicitIt, formatRule(ruleProvider, sourceExplicitIt))
        assertEquals(sourceNamed, formatRule(ruleProvider, sourceNamed))
    }

    @Test
    fun alreadyExplicitItLambdaIsNoOp() {
        val canonical = "val len: (String) -> Int = { it -> it.length }\n"
        assertTrue(lintRule(ruleProvider, canonical).isEmpty())
        assertEquals(canonical, formatRule(ruleProvider, canonical))
        assertFalse(formatRule(ruleProvider, canonical).contains("it -> it ->"))
    }
}
