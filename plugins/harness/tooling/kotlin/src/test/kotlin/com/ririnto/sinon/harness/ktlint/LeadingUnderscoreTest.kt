package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies the leading underscore rule.
 */
class LeadingUnderscoreTest :
    FunSpec({
        test("leaves unreferenced private function parameter unchanged") {
            val source =
                """
                class Example {
                    private fun compute(_unused: Int): Int = 42
                }
                """.trimIndent() + "\n"
            assertSoftly(
                KtLintRuleTestEngine.execute(ruleProvider, source)
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            2,
                            25,
                            "harness:leading-underscore",
                            canBeAutoCorrected = false
                        )
                    )
                formattedCode shouldBe source
            }
        }

        test("keeps legal lambda underscores") {
            val source = "fun ignore(values: List<Int>) = values.map { _: Int -> 42 }\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("autocorrects an unused lambda parameter to a legal underscore") {
            val source = "private fun ignore(values: List<Int>) = values.map { _unused -> 42 }\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            source.indexOf("_unused") + 1,
                            "harness:leading-underscore",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe "private fun ignore(values: List<Int>) = values.map { _ -> 42 }\n"
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
                        "harness:leading-underscore",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("does not rename a referenced lambda parameter") {
            val source = "private fun map(xs: List<Int>) = xs.map { _value -> _value + 1 }\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            source.indexOf("_value") + 1,
                            "harness:leading-underscore",
                            canBeAutoCorrected = false
                        )
                    )
                formattedCode shouldBe source
            }
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
                        "harness:leading-underscore",
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
                        "harness:leading-underscore",
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
                        "harness:leading-underscore",
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
                        "harness:leading-underscore",
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
                        "harness:leading-underscore",
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
                        "harness:leading-underscore",
                        canBeAutoCorrected = false
                    )
                )
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::LeadingUnderscore)
    }
}
