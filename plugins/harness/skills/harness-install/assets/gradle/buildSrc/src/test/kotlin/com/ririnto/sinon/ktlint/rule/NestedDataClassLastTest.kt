package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NestedDataClassLastTest {
    private val ruleProvider: RuleProvider = RuleProvider { NestedDataClassLast() }

    @Test
    fun movesDataClassAfterFunction() {
        val formatted = RuleTestSupport.formatRule(
            ruleProvider,
            """
            class Container {
                data class Row(val id: Int)
                fun lookup(): Row = Row(0)
            }
            """.trimIndent() + "\n"
        )
        assertTrue(formatted.indexOf("fun lookup") < formatted.indexOf("data class Row"))
        assertTrue(RuleTestSupport.lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun leavesDataClassAtBottom() {
        val source = """
            class Container {
                fun lookup(): Row = Row(0)
                data class Row(val id: Int)
            }
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun preservesRelativeOrderOfDataClasses() {
        val formatted = RuleTestSupport.formatRule(
            ruleProvider,
            """
            class Container {
                data class Row1(val id: Int)
                fun lookup(): Row1 = Row1(0)
                data class Row2(val id: Int)
                fun count(): Int = 1
            }
            """.trimIndent() + "\n"
        )
        assertTrue(formatted.indexOf("lookup") < formatted.indexOf("count"))
        assertTrue(formatted.indexOf("Row1") < formatted.indexOf("Row2"))
        assertTrue(RuleTestSupport.lintRule(ruleProvider, formatted).isEmpty())
        assertEquals(formatted, RuleTestSupport.formatRule(ruleProvider, formatted))
    }

    @Test
    fun movesKDocWithDataClass() {
        val formatted = RuleTestSupport.formatRule(
            ruleProvider,
            """
            class Container {
                /**
                 * Description of Row.
                 */
                data class Row(val id: Int)
                fun lookup(): Row = Row(0)
            }
            """.trimIndent() + "\n"
        )
        assertTrue(formatted.indexOf("Description of Row") < formatted.indexOf("data class Row"))
        assertTrue(formatted.indexOf("lookup") < formatted.indexOf("Description of Row"))
    }

    @Test
    fun preservesStandaloneLineCommentBeforeDataClass() {
        val formatted = RuleTestSupport.formatRule(
            ruleProvider,
            """
            class Container {
                // data models live at the bottom
                data class Row(val id: Int)
                fun lookup(): Row = Row(0)
            }
            """.trimIndent() + "\n"
        )
        assertTrue(
            "expected line comment preserved: ${formatted}"
        ) { formatted.contains("// data models live at the bottom") }
        assertTrue(formatted.indexOf("fun lookup") < formatted.indexOf("data class Row"))
        assertTrue(formatted.indexOf("// data models") > formatted.indexOf("lookup"))
        assertTrue(RuleTestSupport.lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun doesNotStealCommentFromPreviousDeclarationAcrossBlankLine() {
        val formatted = RuleTestSupport.formatRule(
            ruleProvider,
            """
            class Container {
                data class Row(val id: Int)

                // belongs to lookup, not the data class
                fun lookup(): Row = Row(0)
            }
            """.trimIndent() + "\n"
        )
        val lookupIdx = formatted.indexOf("fun lookup")
        val rowIdx = formatted.indexOf("data class Row")
        val commentIdx = formatted.indexOf("// belongs to lookup")
        assertTrue("Row must come after lookup: ${formatted}") { lookupIdx < rowIdx }
        assertTrue("comment must survive: ${formatted}") { commentIdx >= 0 }
        assertTrue(
            "comment should stay with lookup, not move with Row: ${formatted}"
        ) { commentIdx < lookupIdx && commentIdx < rowIdx }
        assertTrue(RuleTestSupport.lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun doesNotStealCommentFromPreviousDeclarationAcrossCrlfBlankLine() {
        val formatted = RuleTestSupport.formatRule(
            ruleProvider,
            "class Container {\r\n" +
                "    data class Row(val id: Int)\r\n" +
                "\r\n" +
                "    // belongs to lookup, not the data class\r\n" +
                "    fun lookup(): Row = Row(0)\r\n" +
                "}"
        )
        val lookupIdx = formatted.indexOf("fun lookup")
        val rowIdx = formatted.indexOf("data class Row")
        val commentIdx = formatted.indexOf("// belongs to lookup")
        assertTrue("Row must come after lookup: ${formatted}") { lookupIdx < rowIdx }
        assertTrue("comment must survive: ${formatted}") { commentIdx >= 0 }
        assertTrue(
            "comment should stay with lookup, not move with Row: ${formatted}"
        ) { commentIdx < lookupIdx && commentIdx < rowIdx }
        assertTrue(RuleTestSupport.lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun doesNotStealTrailingCommentFromPreviousDeclaration() {
        val formatted = RuleTestSupport.formatRule(
            ruleProvider,
            """
            class Container {
                data class Row(val id: Int)
                fun lookup(): Row = Row(0) // explains lookup
            }
            """.trimIndent() + "\n"
        )
        val lookupIdx = formatted.indexOf("fun lookup")
        val rowIdx = formatted.indexOf("data class Row")
        val commentIdx = formatted.indexOf("// explains lookup")
        assertTrue("Row must come after lookup: ${formatted}") { lookupIdx < rowIdx }
        assertTrue("comment must survive: ${formatted}") { commentIdx >= 0 }
        assertTrue(
            "trailing comment should stay with lookup, not move with Row: ${formatted}"
        ) { commentIdx > lookupIdx }
        assertTrue(RuleTestSupport.lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun enumClassIsSkipped() {
        val source = """
            enum class Status {
                ACTIVE;

                data class Detail(val code: Int)

                fun label(): String = name
            }
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun checksMultipleEnclosingClasses() {
        assertEquals(
            2,
            RuleTestSupport.lintRule(
                ruleProvider,
                """
                class First {
                    data class Row(val id: Int)
                    fun lookup(): Row = Row(0)
                }
                class Second {
                    data class Entry(val id: Int)
                    fun lookup(): Entry = Entry(0)
                }
                """.trimIndent() + "\n"
            ).size
        )
    }

    @Test
    fun checksNestedClassInsideCompanionObject() {
        val formatted = RuleTestSupport.formatRule(
            ruleProvider,
            """
            class Container {
                companion object {
                    data class Row(val id: Int)
                    fun lookup(): Row = Row(0)
                }
            }
            """.trimIndent() + "\n"
        )
        assertTrue(formatted.indexOf("lookup") < formatted.indexOf("data class Row"))
        assertTrue(RuleTestSupport.lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun keepsForwardReturnTypeReferenceAndIsIdempotent() {
        val formatted = RuleTestSupport.formatRule(
            ruleProvider,
            """
            class Container {
                data class Row(val id: Int)
                fun lookup(): Row = Row(0)
            }
            """.trimIndent() + "\n"
        )
        assertEquals(formatted, RuleTestSupport.formatRule(ruleProvider, formatted))
        assertTrue(RuleTestSupport.lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun preservesClassHeaderWithLambdaTypeInConstructor() {
        val formatted = RuleTestSupport.formatRule(
            ruleProvider,
            """
            class Holder(val callback: () -> Unit = {}) {
                data class Item(val id: Int)
                fun load(): Item = Item(0)
            }
            """.trimIndent() + "\n"
        )
        assertTrue("formatted must contain lambda type: ${formatted}") { formatted.contains("() -> Unit") }
        assertTrue(formatted.indexOf("load") < formatted.indexOf("data class Item"))
        assertTrue(RuleTestSupport.lintRule(ruleProvider, formatted).isEmpty())
        assertEquals(formatted, RuleTestSupport.formatRule(ruleProvider, formatted))
    }

    @Test
    fun leavesRawStringDataClassLintOnly() {
        val source = "class Container {\n" +
            "    data class Item(val text: String = \"\"\"\n" +
            "value\n" +
            "\"\"\")\n" +
            "    fun load(): Item = Item()\n" +
            "}\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }
}
