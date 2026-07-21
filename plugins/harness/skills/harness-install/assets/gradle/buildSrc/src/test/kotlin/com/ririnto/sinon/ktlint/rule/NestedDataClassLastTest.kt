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
    fun preservesStandaloneLineCommentBeforeDataClass() {
        val source = """
            class Container {
                // data models live at the bottom
                data class Row(val id: Int)
                fun lookup(): Row = Row(0)
            }
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
        assertTrue(
            "expected line comment preserved: ${formatted}"
        ) { formatted.contains("// data models live at the bottom") }
        assertTrue(formatted.indexOf("fun lookup") < formatted.indexOf("data class Row"))
        assertTrue(formatted.indexOf("// data models") > formatted.indexOf("lookup"))
        assertTrue(lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun doesNotStealCommentFromPreviousDeclarationAcrossBlankLine() {
        val source = """
            class Container {
                data class Row(val id: Int)

                // belongs to lookup, not the data class
                fun lookup(): Row = Row(0)
            }
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
        val lookupIdx = formatted.indexOf("fun lookup")
        val rowIdx = formatted.indexOf("data class Row")
        val commentIdx = formatted.indexOf("// belongs to lookup")
        assertTrue("Row must come after lookup: ${formatted}") { lookupIdx < rowIdx }
        assertTrue("comment must survive: ${formatted}") { commentIdx >= 0 }
        assertTrue(
            "comment should stay with lookup, not move with Row: ${formatted}"
        ) { commentIdx < lookupIdx && commentIdx < rowIdx }
        assertTrue(lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun doesNotStealCommentFromPreviousDeclarationAcrossCrlfBlankLine() {
        val source = (
            "class Container {\r\n" +
                "    data class Row(val id: Int)\r\n" +
                "\r\n" +
                "    // belongs to lookup, not the data class\r\n" +
                "    fun lookup(): Row = Row(0)\r\n" +
                "}"
            )
        val formatted = formatRule(ruleProvider, source)
        val lookupIdx = formatted.indexOf("fun lookup")
        val rowIdx = formatted.indexOf("data class Row")
        val commentIdx = formatted.indexOf("// belongs to lookup")
        assertTrue("Row must come after lookup: ${formatted}") { lookupIdx < rowIdx }
        assertTrue("comment must survive: ${formatted}") { commentIdx >= 0 }
        assertTrue(
            "comment should stay with lookup, not move with Row: ${formatted}"
        ) { commentIdx < lookupIdx && commentIdx < rowIdx }
        assertTrue(lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun doesNotStealTrailingCommentFromPreviousDeclaration() {
        val source = """
            class Container {
                data class Row(val id: Int)
                fun lookup(): Row = Row(0) // explains lookup
            }
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
        val lookupIdx = formatted.indexOf("fun lookup")
        val rowIdx = formatted.indexOf("data class Row")
        val commentIdx = formatted.indexOf("// explains lookup")
        assertTrue("Row must come after lookup: ${formatted}") { lookupIdx < rowIdx }
        assertTrue("comment must survive: ${formatted}") { commentIdx >= 0 }
        assertTrue(
            "trailing comment should stay with lookup, not move with Row: ${formatted}"
        ) { commentIdx > lookupIdx }
        assertTrue(lintRule(ruleProvider, formatted).isEmpty())
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
