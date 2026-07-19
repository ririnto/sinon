package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeadingUnderscoreKtlintRuleTest {
    private val ruleProvider = RuleProvider { LeadingUnderscoreKtlintRule() }

    @Test
    fun autocorrectsUnreferencedPrivateFunctionParameter() {
        val source = """
            class Example {
                private fun compute(_unused: Int): Int = 42
            }
        """.trimIndent() + "\n"
        val expected = """
            class Example {
                private fun compute(_: Int): Int = 42
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertEquals(RuleId("code:leading-underscore"), errors.single().ruleId)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(expected, formatRule(ruleProvider, source))
    }

    @Test
    fun autocorrectIsIdempotentOnRenamedParameter() {
        val source = """
            class Example {
                private fun compute(_: Int): Int = 42
            }
        """.trimIndent() + "\n"
        assertTrue(lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyWhenParameterIsReferencedInBody() {
        val source = """
            class Example {
                private fun compute(_value: Int): Int = _value + 1
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyWhenFunctionIsPublic() {
        val source = """
            class Example {
                fun compute(_unused: Int): Int = 42
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun acceptsParameterWithoutLeadingUnderscore() {
        val source = """
            class Example {
                private fun compute(value: Int): Int = value + 1
            }
        """.trimIndent() + "\n"
        assertTrue(lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyWhenParameterReferencedInSiblingDefaultValue() {
        val source = """
            class Example {
                private fun compute(_base: Int, other: Int = _base): Int = other
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyWhenCalledWithNamedArgument() {
        val source = """
            class Example {
                private fun compute(_unused: Int): Int = 42

                fun caller(): Int = compute(_unused = 5)
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyWhenParameterIsValInPrimaryConstructor() {
        val source = """
            class Example(private val _id: Int)
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyWhenDeclarationIsProperty() {
        val source = """
            class Example {
                private val _value: Int = 42
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyWhenFunctionIsOverride() {
        val source = """
            abstract class Base {
                abstract fun compute(_unused: Int): Int
            }
            class Derived : Base() {
                override fun compute(_unused: Int): Int = 42
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertTrue(errors.size == 2)
        assertTrue(errors.all { !it.canBeAutoCorrected })
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun flagsFileBasenameAndLeavesLintOnly() {
        val source = """
            class Example
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source, fileName = "_Sample.kt")
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source, fileName = "_Sample.kt"))
    }
}
