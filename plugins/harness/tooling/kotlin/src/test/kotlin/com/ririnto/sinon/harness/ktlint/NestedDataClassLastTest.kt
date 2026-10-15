package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies the nested data class last rule.
 */
class NestedDataClassLastTest :
    FunSpec({
        test("moves data class after function") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    class Container {
                        data class Row(val id: Int)
                        fun lookup(): Row = Row(0)
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            2,
                            16,
                            "harness:nested-data-class-last",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    class Container {
                        fun lookup(): Row = Row(0)

                        data class Row(val id: Int)
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("leaves data class at bottom") {
            val source =
                """
                class Container {
                    fun lookup(): Row = Row(0)
                    data class Row(val id: Int)
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("preserves relative order of data classes") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
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
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(2, 16, "harness:nested-data-class-last"),
                        KtLintRuleTestEngine.Diagnostic(4, 16, "harness:nested-data-class-last")
                    )
                formattedCode shouldBe
                    """
                    class Container {
                        fun lookup(): Row1 = Row1(0)

                        fun count(): Int = 1

                        data class Row1(val id: Int)

                        data class Row2(val id: Int)
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("moves kdoc with data class") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
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
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            5,
                            16,
                            "harness:nested-data-class-last",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    class Container {
                        fun lookup(): Row = Row(0)

                        /**
                         * Description of Row.
                         */
                        data class Row(val id: Int)
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("preserves standalone line comment before data class") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    class Container {
                        // data models live at the bottom
                        data class Row(val id: Int)
                        fun lookup(): Row = Row(0)
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            3,
                            16,
                            "harness:nested-data-class-last",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    class Container {
                        fun lookup(): Row = Row(0)

                        // data models live at the bottom
                        data class Row(val id: Int)
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("does not steal comment from previous declaration across blank line") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    class Container {
                        data class Row(val id: Int)

                        // belongs to lookup, not the data class
                        fun lookup(): Row = Row(0)
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            2,
                            16,
                            "harness:nested-data-class-last",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    class Container {
                        // belongs to lookup, not the data class
                            fun lookup(): Row = Row(0)

                        data class Row(val id: Int)
                    }
                    """.trimIndent() + "\n"
            }
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
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("checks multiple enclosing classes") {
            KtLintRuleTestEngine
                .execute(
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
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        16,
                        "harness:nested-data-class-last",
                        canBeAutoCorrected = true
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        6,
                        16,
                        "harness:nested-data-class-last",
                        canBeAutoCorrected = true
                    )
                )
        }

        test("checks nested class inside companion object") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
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
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            3,
                            20,
                            "harness:nested-data-class-last",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    class Container {
                        companion object {
                        fun lookup(): Row = Row(0)

                        data class Row(val id: Int)
                    }
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("keeps forward return type reference and is idempotent") {
            val source =
                """
                class Container {
                    fun lookup(): Row = Row(0)
                    data class Row(val id: Int)
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("leaves raw string data class lint only") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    class Container {
                        data class Item(val text: String = ${"\"\"\""}
                    value
                    ${"\"\"\""})
                        fun load(): Item = Item()
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        16,
                        "harness:nested-data-class-last",
                        canBeAutoCorrected = false
                    )
                )
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::NestedDataClassLast)
    }
}
