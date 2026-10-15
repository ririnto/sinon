package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies the unstructured logging rule.
 */
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
                        "harness:unstructured-logging",
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
                        "harness:unstructured-logging",
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
                        "harness:unstructured-logging",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        15,
                        "harness:unstructured-logging",
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

        test("print and standard stream calls are flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun log() {
                        print("one")
                        kotlin.io.print("two")
                        System.out.println("three")
                        System.out.print("four")
                        System.out.printf("five %s", "x")
                        System.err.println("six")
                        System.err.print("seven")
                        java.lang.System.out.println("eight")
                    }
                    """.trimIndent() + "\n"
                ).diagnostics
                .map(KtLintRuleTestEngine.Diagnostic::ruleId) shouldContainExactlyInAnyOrder
                List(8) { "harness:unstructured-logging" }
        }

        test("print stack trace is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun log(error: Throwable) {
                        error.printStackTrace()
                        this.printStackTrace()
                    }
                    """.trimIndent() + "\n"
                ).diagnostics
                .map(KtLintRuleTestEngine.Diagnostic::line) shouldContainExactlyInAnyOrder listOf(2, 3)
        }

        test("receiver print calls and other standard stream members are safe") {
            val source =
                """
                class Printer {
                    fun print(message: String) {}
                }

                fun log(printer: Printer) {
                    printer.print("message")
                    System.out.flush()
                    System.lineSeparator()
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("shadowed console functions and stream roots are safe") {
            listOf(
                """
                fun print(message: String) {}
                fun log() = print("message")
                """.trimIndent() + "\n",
                """
                fun log(println: (String) -> Unit, System: Console) {
                    println("message")
                    System.out.println("message")
                }
                """.trimIndent() + "\n",
                """
                import example.println

                fun log() = println("message")
                """.trimIndent() + "\n",
                """
                fun log(pairs: List<Pair<Console, Console>>) {
                    pairs.forEach { (System, other) ->
                        System.out.println("message")
                    }
                    for ((System, other) in pairs) {
                        System.err.print("message")
                    }
                }
                """.trimIndent() + "\n",
                """
                fun log() = System().out.println("message")
                """.trimIndent() + "\n"
            ).forEach { source ->
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    diagnostics shouldContainExactlyInAnyOrder emptyList()
                    formattedCode shouldBe source
                }
            }
        }

        test("a console-like member in another class does not hide standard output") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    class Printer {
                        fun println(message: String) {}
                    }

                    fun log() = println("message")
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        5,
                        13,
                        "harness:unstructured-logging",
                        canBeAutoCorrected = false
                    )
                )
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
