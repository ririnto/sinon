package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class CompanionObjectPositionTest :
    FunSpec({
        test("flags companion object after other declarations") {
            val source =
                """
                class Example {
                    val value: String = "value"

                    companion object
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        15,
                        "place the companion object before other class members",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("accepts companion object at first position") {
            val source =
                """
                class Example {
                    companion object

                    val value: String = "value"
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

        test("accepts class without companion object") {
            val source =
                """
                class Example {
                    val value: String = "value"
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

        test("ignores enum entries when computing first position") {
            val source =
                """
                enum class Status {
                    ACTIVE;

                    companion object {
                        const val DEFAULT = "active"
                    }
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

        test("checks nested classes independently") {
            val source =
                """
                class Outer {
                    val outerValue: String = "outer"

                    class Inner {
                        val innerValue: String = "inner"

                        companion object
                    }
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        7,
                        19,
                        "place the companion object before other class members",
                        canBeAutoCorrected = false
                    )
                )
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::CompanionObjectPosition)
    }
}
