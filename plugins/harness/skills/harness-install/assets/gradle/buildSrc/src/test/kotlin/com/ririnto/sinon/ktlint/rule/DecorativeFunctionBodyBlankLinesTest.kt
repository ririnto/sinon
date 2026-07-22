package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecorativeFunctionBodyBlankLinesTest {
    private val ruleProvider: RuleProvider = RuleProvider { DecorativeFunctionBodyBlankLines() }

    @Test
    fun singleBlankLineBetweenDeclarationsIsSkipped() {
        val source = """
            fun foo() {
                val x = 1

                val y = 2
            }
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun singleBlankLineBetweenCallAndCallIsFlagged() {
        val source = """
            fun foo() {
                println(1)

                println(2)
            }
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "fun foo() {\n    println(1)\n    println(2)\n}\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun singleBlankLineBetweenDeclarationAndCallIsFlagged() {
        val source = """
            fun foo() {
                val x = 1

                println(x)
            }
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "fun foo() {\n    val x = 1\n    println(x)\n}\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun singleBlankLineBetweenCallAndDeclarationIsSkipped() {
        val source = """
            fun foo() {
                println(1)

                val x = 2
            }
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun singleBlankLineBetweenAnnotationAndDeclarationIsSkipped() {
        val source = """
            fun foo() {
                println(1)

                @Suppress("UNCHECKED_CAST")
                val x = 2 as Int
            }
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
    }

    @Test
    fun multipleBlankLinesInFunctionBodyAreCollapsed() {
        val source = """
            fun foo() {
                println(1)


                println(2)
            }
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            """
            fun foo() {
                println(1)
                println(2)
            }
            """.trimIndent() + "\n",
            RuleTestSupport.formatRule(ruleProvider, source)
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
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun blankLineBetweenLocalFunctionsIsSkipped() {
        val source = """
            fun foo() {
                fun first() = 1


                fun second() = 2
            }
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
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
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
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
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
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
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
    }

    @Test
    fun formattingIsIdempotentAndLeavesNoLintErrors() {
        val source = """
            fun foo() {
                println(1)


                println(2)
            }
        """.trimIndent() + "\n"
        val formatted = RuleTestSupport.formatRule(ruleProvider, source)
        assertEquals(formatted, RuleTestSupport.formatRule(ruleProvider, formatted))
        assertTrue(RuleTestSupport.lintRule(ruleProvider, formatted).isEmpty())
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
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun collapsedBlankLineRemainsAutocorrectableAndIdempotent() {
        val source = """
            fun sample() {

                work()
            }
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        val formatted = RuleTestSupport.formatRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("fun sample() {\n    work()\n}\n", formatted)
        assertEquals(formatted, RuleTestSupport.formatRule(ruleProvider, formatted))
    }

    @Test
    fun blankLineInLambdaBodyInsideFunctionIsNotFlagged() {
        val source = """
            fun foo() {
                listOf(1).map { value ->
                    val doubled = value * 2

                    doubled.toString()
                }
            }
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
    }

    @Test
    fun blankLineInAnonymousObjectBodyInsideFunctionIsNotFlagged() {
        val source = """
            fun foo() {
                object {
                    val value = 1

                    fun getValue() = value
                }
            }
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
    }
}
