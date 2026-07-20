package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanionObjectPositionTest {
    private val ruleProvider = RuleProvider { CompanionObjectPosition() }

    @Test
    fun flagsCompanionObjectAfterOtherDeclarations() {
        val source = """
            class Example {
                val value: String = "value"

                companion object
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertEquals(RuleId("code:companion-object-position"), errors.single().ruleId)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun acceptsCompanionObjectAtFirstPosition() {
        val source = """
            class Example {
                companion object

                val value: String = "value"
            }
        """.trimIndent() + "\n"
        assertTrue(lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun acceptsClassWithoutCompanionObject() {
        val source = """
            class Example {
                val value: String = "value"
            }
        """.trimIndent() + "\n"
        assertTrue(lintRule(ruleProvider, source).isEmpty())
    }

    @Test
    fun ignoresEnumEntriesWhenComputingFirstPosition() {
        val source = """
            enum class Status {
                ACTIVE;

                companion object {
                    const val DEFAULT = "active"
                }
            }
        """.trimIndent() + "\n"
        assertTrue(lintRule(ruleProvider, source).isEmpty())
    }

    @Test
    fun checksNestedClassesIndependently() {
        val source = """
            class Outer {
                val outerValue: String = "outer"

                class Inner {
                    val innerValue: String = "inner"

                    companion object
                }
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }
}
