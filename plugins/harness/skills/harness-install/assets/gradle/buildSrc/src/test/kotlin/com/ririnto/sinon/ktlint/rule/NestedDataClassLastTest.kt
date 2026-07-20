package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NestedDataClassLastTest {
    private val ruleProvider = RuleProvider { NestedDataClassLast() }

    @Test
    fun movesDataClassAfterFunction() {
        val source = """
            class Container {
                data class Row(val id: Int)
                fun lookup(): Row = Row(0)
            }
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
        assertTrue(formatted.indexOf("fun lookup") < formatted.indexOf("data class Row"))
        assertTrue(lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun leavesDataClassAtBottom() {
        val source = """
            class Container {
                fun lookup(): Row = Row(0)
                data class Row(val id: Int)
            }
        """.trimIndent() + "\n"
        assertTrue(lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun preservesRelativeOrderOfDataClasses() {
        val source = """
            class Container {
                data class Row1(val id: Int)
                fun lookup(): Row1 = Row1(0)
                data class Row2(val id: Int)
                fun count(): Int = 1
            }
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
        assertTrue(formatted.indexOf("lookup") < formatted.indexOf("count"))
        assertTrue(formatted.indexOf("Row1") < formatted.indexOf("Row2"))
        assertTrue(lintRule(ruleProvider, formatted).isEmpty())
        assertEquals(formatted, formatRule(ruleProvider, formatted))
    }

    @Test
    fun movesKDocWithDataClass() {
        val source = """
            class Container {
                /**
                 * Description of Row.
                 */
                data class Row(val id: Int)
                fun lookup(): Row = Row(0)
            }
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
        assertTrue(formatted.indexOf("Description of Row") < formatted.indexOf("data class Row"))
        assertTrue(formatted.indexOf("Description of Row") > formatted.indexOf("lookup"))
    }

    @Test
    fun checksMultipleEnclosingClasses() {
        val source = """
            class First {
                data class Row(val id: Int)
                fun lookup(): Row = Row(0)
            }
            class Second {
                data class Entry(val id: Int)
                fun lookup(): Entry = Entry(0)
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(2, errors.size)
    }

    @Test
    fun checksNestedClassInsideCompanionObject() {
        val source = """
            class Container {
                companion object {
                    data class Row(val id: Int)
                    fun lookup(): Row = Row(0)
                }
            }
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
        assertTrue(formatted.indexOf("lookup") < formatted.indexOf("data class Row"))
        assertTrue(lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun keepsForwardReturnTypeReferenceAndIsIdempotent() {
        val source = """
            class Container {
                data class Row(val id: Int)
                fun lookup(): Row = Row(0)
            }
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
        assertEquals(formatted, formatRule(ruleProvider, formatted))
        assertTrue(lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun preservesClassHeaderWithLambdaTypeInConstructor() {
        val source = """
            class Holder(val callback: () -> Unit = {}) {
                data class Item(val id: Int)
                fun load(): Item = Item(0)
            }
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
        assertTrue("formatted must contain lambda type: ${formatted}") { formatted.contains("() -> Unit") }
        assertTrue(formatted.indexOf("load") < formatted.indexOf("data class Item"))
        assertTrue(lintRule(ruleProvider, formatted).isEmpty())
        assertEquals(formatted, formatRule(ruleProvider, formatted))
    }
}
