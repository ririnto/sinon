package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class UnstructuredLoggingTest :
    FunSpec({
        test("println is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun log() = println("message")
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        13,
                        "unstructured logging `println`; use structured logger",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("qualified println is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun log() = kotlin.io.println("message")
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        23,
                        "unstructured logging `kotlin.io.println`; use structured logger",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("multiple println calls are all flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun log() {
                        println("one")
                        kotlin.io.println("two")
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        5,
                        "unstructured logging `println`; use structured logger",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        15,
                        "unstructured logging `kotlin.io.println`; use structured logger",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("receiver println is safe") {
            val source =
                """
                class Logger { fun println(msg: String) {} }
                fun log(l: Logger) = l.println("message")
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("non kotlin callee is safe") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun log(error: Throwable) = error.printStackTrace()\n")) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe "fun log(error: Throwable) = error.printStackTrace()\n"
            }
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun log() = print("message")
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe
                    """
                    fun log() = print("message")
                    """.trimIndent() + "\n"
            }
        }

        test("safe access and not null assertion receivers are safe") {
            val source =
                """
                class Logger {
                    fun println(message: String) {}
                }

                fun log(foo: Logger?) {
                    foo?.println("msg")
                    foo!!.println("msg")
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::UnstructuredLogging)
    }
}
