package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class LeadingUnderscoreTest :
    FunSpec({
        test("autocorrects unreferenced private function parameter") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    class Example {
                        private fun compute(_unused: Int): Int = 42
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            2,
                            25,
                            "remove the leading underscore from declaration `_unused`",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    class Example {
                        private fun compute(_: Int): Int = 42
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("leaves lint only when parameter is referenced in body") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    class Example {
                        private fun compute(_value: Int): Int = _value + 1
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        25,
                        "remove the leading underscore from declaration `_value`",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("leaves lint only when function is public") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    class Example {
                        fun compute(_unused: Int): Int = 42
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        17,
                        "remove the leading underscore from declaration `_unused`",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("accepts parameter without leading underscore") {
            val source =
                """
                class Example {
                    private fun compute(value: Int): Int = value + 1
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("leaves lint only when parameter referenced in sibling default value") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    class Example {
                        private fun compute(_base: Int, other: Int = _base): Int = other
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        25,
                        "remove the leading underscore from declaration `_base`",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("leaves lint only when called with named argument") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    class Example {
                        private fun compute(_unused: Int): Int = 42

                        fun caller(): Int = compute(_unused = 5)
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        25,
                        "remove the leading underscore from declaration `_unused`",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("leaves lint only when parameter is val in primary constructor") {
            KtLintRuleTestEngine.execute(ruleProvider, "class Example(private val _id: Int)\n").diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        27,
                        "remove the leading underscore from declaration `_id`",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("leaves lint only when declaration is property") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    class Example {
                        private val _value: Int = 42
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        17,
                        "remove the leading underscore from declaration `_value`",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("accepts override parameter with leading underscore") {
            val source =
                """
                interface Base {
                    fun compute(_unused: Int): Int
                }

                class Impl : Base {
                    override fun compute(_unused: Int): Int = 0
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("accepts override property with leading underscore") {
            val source =
                """
                open class Base {
                    open val _value: Int = 0
                }

                class Derived : Base() {
                    override val _value: Int = 1
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("rejects non override property with leading underscore") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    class C {
                        val _value: Int = 0
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        9,
                        "remove the leading underscore from declaration `_value`",
                        canBeAutoCorrected = false
                    )
                )
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::LeadingUnderscore)
    }
}
