package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class NestedDataClassLastTest :
    FunSpec({
        test("moves data class after function") {
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
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        16,
                        "move the nested data class `Row` to the bottom of its enclosing class",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("leaves data class at bottom") {
            val source =
                """
                class Container {
                    fun lookup(): Row = Row(0)
                    data class Row(val id: Int)
                }
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("preserves relative order of data classes") {
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
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(2, 16, "move the nested data class `Row1` to the bottom of its enclosing class"),
                    KtLintRuleTestEngine.Diagnostic(4, 16, "move the nested data class `Row2` to the bottom of its enclosing class")
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("moves kdoc with data class") {
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
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        5,
                        16,
                        "move the nested data class `Row` to the bottom of its enclosing class",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("preserves standalone line comment before data class") {
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
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        16,
                        "move the nested data class `Row` to the bottom of its enclosing class",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("does not steal comment from previous declaration across blank line") {
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
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        16,
                        "move the nested data class `Row` to the bottom of its enclosing class",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("enum class is skipped") {
            val source =
                """
                enum class Status {
                    ACTIVE;

                    data class Detail(val code: Int)

                    fun label(): String = name
                }
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("checks multiple enclosing classes") {
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
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        16,
                        "move the nested data class `Row` to the bottom of its enclosing class",
                        canBeAutoCorrected = true
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        6,
                        16,
                        "move the nested data class `Entry` to the bottom of its enclosing class",
                        canBeAutoCorrected = true
                    )
                )
        }

        test("checks nested class inside companion object") {
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
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        20,
                        "move the nested data class `Row` to the bottom of its enclosing class",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("keeps forward return type reference and is idempotent") {
            val source =
                """
                class Container {
                    fun lookup(): Row = Row(0)
                    data class Row(val id: Int)
                }
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("leaves raw string data class lint only") {
            val source =
                "class Container {\n" +
                    "    data class Item(val text: String = \"\"\"\n" +
                    "value\n" +
                    "\"\"\")\n" +
                    "    fun load(): Item = Item()\n" +
                    "}\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        16,
                        "move the nested data class `Item` to the bottom of its enclosing class",
                        canBeAutoCorrected = false
                    )
                )
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::NestedDataClassLast)
    }
}
