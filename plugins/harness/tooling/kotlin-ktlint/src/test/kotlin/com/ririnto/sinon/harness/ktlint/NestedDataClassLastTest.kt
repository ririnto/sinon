@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class NestedDataClassLastTest {
    private val assertThat = assertThatRule { NestedDataClassLast() }

    @Test
    fun movesDataClassAfterFunction() {
        val source =
            """
            class Container {
                data class Row(val id: Int)
                fun lookup(): Row = Row(0)
            }
            """.trimIndent() + "\n"
        val expected =
            """
            class Container {
                fun lookup(): Row = Row(0)

                data class Row(val id: Int)
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolation(2, 16, "move the nested data class `Row` to the bottom of its enclosing class")
            .isFormattedAs(expected)
    }

    @Test
    fun leavesDataClassAtBottom() {
        val source =
            """
            class Container {
                fun lookup(): Row = Row(0)
                data class Row(val id: Int)
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }

    @Test
    fun preservesRelativeOrderOfDataClasses() {
        val source =
            """
            class Container {
                data class Row1(val id: Int)
                fun lookup(): Row1 = Row1(0)
                data class Row2(val id: Int)
                fun count(): Int = 1
            }
            """.trimIndent() + "\n"
        val expected =
            """
            class Container {
                fun lookup(): Row1 = Row1(0)

                fun count(): Int = 1

                data class Row1(val id: Int)

                data class Row2(val id: Int)
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolations(
                LintViolation(2, 16, "move the nested data class `Row1` to the bottom of its enclosing class"),
                LintViolation(4, 16, "move the nested data class `Row2` to the bottom of its enclosing class")
            ).isFormattedAs(expected)
    }

    @Test
    fun movesKDocWithDataClass() {
        val source =
            """
            class Container {
                /**
                 * Description of Row.
                 */
                data class Row(val id: Int)
                fun lookup(): Row = Row(0)
            }
            """.trimIndent() + "\n"
        val expected =
            """
            class Container {
                fun lookup(): Row = Row(0)

                /**
                 * Description of Row.
                 */
                data class Row(val id: Int)
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolation(5, 16, "move the nested data class `Row` to the bottom of its enclosing class")
            .isFormattedAs(expected)
    }

    @Test
    fun preservesStandaloneLineCommentBeforeDataClass() {
        val source =
            """
            class Container {
                // data models live at the bottom
                data class Row(val id: Int)
                fun lookup(): Row = Row(0)
            }
            """.trimIndent() + "\n"
        val expected =
            """
            class Container {
                fun lookup(): Row = Row(0)

                // data models live at the bottom
                data class Row(val id: Int)
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolation(3, 16, "move the nested data class `Row` to the bottom of its enclosing class")
            .isFormattedAs(expected)
    }

    @Test
    fun doesNotStealCommentFromPreviousDeclarationAcrossBlankLine() {
        val source =
            """
            class Container {
                data class Row(val id: Int)

                // belongs to lookup, not the data class
                fun lookup(): Row = Row(0)
            }
            """.trimIndent() + "\n"
        val expected =
            """
            class Container {
                // belongs to lookup, not the data class
                    fun lookup(): Row = Row(0)

                data class Row(val id: Int)
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolation(2, 16, "move the nested data class `Row` to the bottom of its enclosing class")
            .isFormattedAs(expected)
    }

    @Test
    fun enumClassIsSkipped() {
        val source =
            """
            enum class Status {
                ACTIVE;

                data class Detail(val code: Int)

                fun label(): String = name
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }

    @Test
    fun checksMultipleEnclosingClasses() {
        val source =
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
        assertThat(source).hasLintViolationsWithoutAutoCorrect(
            LintViolation(2, 16, "move the nested data class `Row` to the bottom of its enclosing class"),
            LintViolation(6, 16, "move the nested data class `Entry` to the bottom of its enclosing class")
        )
    }

    @Test
    fun checksNestedClassInsideCompanionObject() {
        val source =
            """
            class Container {
                companion object {
                    data class Row(val id: Int)
                    fun lookup(): Row = Row(0)
                }
            }
            """.trimIndent() + "\n"
        val expected =
            """
            class Container {
                companion object {
                fun lookup(): Row = Row(0)

                data class Row(val id: Int)
            }
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolation(3, 20, "move the nested data class `Row` to the bottom of its enclosing class")
            .isFormattedAs(expected)
    }

    @Test
    fun keepsForwardReturnTypeReferenceAndIsIdempotent() {
        val source =
            """
            class Container {
                fun lookup(): Row = Row(0)
                data class Row(val id: Int)
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }

    @Test
    fun leavesRawStringDataClassLintOnly() {
        val source =
            "class Container {\n" +
                "    data class Item(val text: String = \"\"\"\n" +
                "value\n" +
                "\"\"\")\n" +
                "    fun load(): Item = Item()\n" +
                "}\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(2, 16, "move the nested data class `Item` to the bottom of its enclosing class")
    }
}
