package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeadingUnderscoreTest {
    private val ruleProvider: RuleProvider = RuleProvider { LeadingUnderscore() }

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
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertEquals(RuleId("code:leading-underscore"), errors.single().ruleId)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(expected, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun autocorrectIsIdempotentOnRenamedParameter() {
        val source = """
            class Example {
                private fun compute(_: Int): Int = 42
            }
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyWhenParameterIsReferencedInBody() {
        val source = """
            class Example {
                private fun compute(_value: Int): Int = _value + 1
            }
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyWhenFunctionIsPublic() {
        val source = """
            class Example {
                fun compute(_unused: Int): Int = 42
            }
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun acceptsParameterWithoutLeadingUnderscore() {
        val source = """
            class Example {
                private fun compute(value: Int): Int = value + 1
            }
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyWhenParameterReferencedInSiblingDefaultValue() {
        val source = """
            class Example {
                private fun compute(_base: Int, other: Int = _base): Int = other
            }
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyWhenCalledWithNamedArgument() {
        val source = """
            class Example {
                private fun compute(_unused: Int): Int = 42

                fun caller(): Int = compute(_unused = 5)
            }
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyWhenParameterIsValInPrimaryConstructor() {
        val source = """
            class Example(private val _id: Int)
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyWhenDeclarationIsProperty() {
        val source = """
            class Example {
                private val _value: Int = 42
            }
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun acceptsOverrideParameterWithLeadingUnderscore() {
        val source = """
            interface Base {
                fun compute(_unused: Int): Int
            }

            class Impl : Base {
                override fun compute(_unused: Int): Int = 0
            }
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertTrue(errors.isEmpty(), "override parameter should be allowed: ${errors}")
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun acceptsOverridePropertyWithLeadingUnderscore() {
        val source = """
            open class Base {
                open val _value: Int = 0
            }

            class Derived : Base() {
                override val _value: Int = 1
            }
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
    }

    @Test
    fun acceptsInterfacePropertyWithLeadingUnderscore() {
        val source = """
            interface I {
                val _value: Int
            }

            class C : I {
                override val _value: Int = 0
            }
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
    }

    @Test
    fun rejectsNonOverridePropertyWithLeadingUnderscore() {
        val source = """
            class C {
                val _value: Int = 0
            }
        """.trimIndent() + "\n"
        assertEquals(1, RuleTestSupport.lintRule(ruleProvider, source).size)
    }
}
