package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecorativeFunctionBodyBlankLinesTest {
    private val ruleProvider = RuleProvider { DecorativeFunctionBodyBlankLines() }

    @Test
    fun singleBlankLineInFunctionBodyIsNotFlagged() {
        val source = """
            fun foo() {
                val x = 1

                val y = 2
            }
        """.trimIndent() + "\n"

        assertTrue(lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun multipleBlankLinesInFunctionBodyAreCollapsed() {
        val source = """
            fun foo() {
                println(1)


                println(2)
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)

        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            """
            fun foo() {
                println(1)
                println(2)
            }
            """.trimIndent() + "\n",
            formatRule(ruleProvider, source)
        )
    }

    @Test
    fun blankLineBetweenLocalDeclarationsIsSkipped() {
        val source = """
            fun foo() {
                val x = 1


                val y = 2
            }
        """.trimIndent() + "\n"

        assertTrue(lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun blankLineBetweenLocalFunctionsIsSkipped() {
        val source = """
            fun foo() {
                fun first() = 1


                fun second() = 2
            }
        """.trimIndent() + "\n"

        assertTrue(lintRule(ruleProvider, source).isEmpty())
    }

    @Test
    fun blankLineAdjacentToLineCommentIsSkipped() {
        val source = """
            fun foo() {
                val x = 1


                // comment
                val y = 2
            }
        """.trimIndent() + "\n"

        assertTrue(lintRule(ruleProvider, source).isEmpty())
    }

    @Test
    fun blankLineAdjacentToBlockCommentIsSkipped() {
        val source = """
            fun foo() {
                val x = 1


                /* comment */
                val y = 2
            }
        """.trimIndent() + "\n"

        assertTrue(lintRule(ruleProvider, source).isEmpty())
    }

    @Test
    fun blankLineAdjacentToKDocIsSkipped() {
        val source = """
            fun foo() {
                val x = 1


                /** documentation */
                val y = 2
            }
        """.trimIndent() + "\n"

        assertTrue(lintRule(ruleProvider, source).isEmpty())
    }

    @Test
    fun formattingIsIdempotentAndLeavesNoLintErrors() {
        val source = """
            fun foo() {
                println(1)


                println(2)
            }
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)

        assertEquals(formatted, formatRule(ruleProvider, formatted))
        assertTrue(lintRule(ruleProvider, formatted).isEmpty())
    }

    @Test
    fun nestedFunctionBodyPreservesEightSpaceIndentation() {
        val source = """
            class C {
                fun foo() {
                    println(1)


                    println(2)
                }
            }
        """.trimIndent() + "\n"

        assertEquals(
            """
            class C {
                fun foo() {
                    println(1)
                    println(2)
                }
            }
            """.trimIndent() + "\n",
            formatRule(ruleProvider, source)
        )
    }

    @Test
    fun collapsedBlankLineRemainsAutocorrectableAndIdempotent() {
        val source = """
            fun sample() {

                work()
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        val formatted = formatRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("fun sample() {\n    work()\n}\n", formatted)
        assertEquals(formatted, formatRule(ruleProvider, formatted))
    }
}
