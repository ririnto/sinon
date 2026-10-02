package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ExplicitUnitBranchTest :
    FunSpec({
        test("flags only explicit unit results in mixed when branches") {
            val source =
                """
                fun sample(value: Int) {
                    when (value) {
                        0 -> Unit
                        1 -> value
                        2 -> kotlin.Unit
                        3 -> value.toString()
                        4 -> kotlin . Unit
                        5 -> (Unit)
                        6 -> {
                            val ignored = value
                            Unit
                        }
                        else -> Unit
                    }
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(3, 14, "explicit Unit branch result is forbidden", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(5, 14, "explicit Unit branch result is forbidden", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(7, 14, "explicit Unit branch result is forbidden", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(8, 14, "explicit Unit branch result is forbidden", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(9, 14, "explicit Unit branch result is forbidden", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(13, 17, "explicit Unit branch result is forbidden", canBeAutoCorrected = false)
                )
        }

        test("ignores non explicit unit branch results") {
            listOf(
                "0 -> {}",
                "0 -> value.toString()",
                "0 -> branch@ Unit",
                "0 -> return",
                "0 -> other.Unit",
                "0 -> { Unit; value }"
            ).forEach { branch ->
                val source =
                    """
                    fun sample(value: Int) {
                        when (value) {
                            $branch
                            else -> value
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
        }

        test("flags unit result in if without else") {
            val source =
                """
                fun sample(condition: Boolean) {
                    if (condition) Unit
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(KtLintRuleTestEngine.Diagnostic(2, 20, "explicit Unit branch result is forbidden", canBeAutoCorrected = false))
        }

        test("flags both branches in simple if else") {
            val source =
                """
                fun sample(condition: Boolean) {
                    if (condition) Unit else Unit
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(2, 20, "explicit Unit branch result is forbidden", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(2, 30, "explicit Unit branch result is forbidden", canBeAutoCorrected = false)
                )
        }

        test("flags each unit result once in else if chain") {
            val source =
                """
                fun sample(first: Boolean, second: Boolean) {
                    if (first) Unit else if (second) Unit else Unit
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(2, 16, "explicit Unit branch result is forbidden", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(2, 38, "explicit Unit branch result is forbidden", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(2, 48, "explicit Unit branch result is forbidden", canBeAutoCorrected = false)
                )
        }

        test("traverses nested control flow without flagging outer branches") {
            val source =
                """
                fun sample(value: Int, condition: Boolean) {
                    when (value) {
                        0 -> if (condition) Unit else value
                        1 -> when (value) {
                            0 -> Unit
                            else -> value
                        }
                        else -> value
                    }
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(3, 29, "explicit Unit branch result is forbidden", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(5, 18, "explicit Unit branch result is forbidden", canBeAutoCorrected = false)
                )
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::ExplicitUnitBranch)
    }
}
