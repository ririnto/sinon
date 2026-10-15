package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies that inline comments are rejected only inside function bodies.
 */
class NoLineCommentTest :
    FunSpec({
        test("required license and suppression directives are preserved") {
            val source = "fun work() {\n/* @license MIT */\n// noinspection UnusedVariable\nval value = 1\n}\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("standalone line comment in a function body is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun foo() {
                        // comment
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder listOf(bodyComment(2, 5))
        }

        test("trailing line comment in a function body is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun foo() {
                        val x = 1 // comment
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder listOf(bodyComment(2, 15))
        }

        test("block comment in a function body is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun foo() {
                        /* block */
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder listOf(bodyComment(2, 5))
        }

        test("mixed comments in a function body are all flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun foo() {
                        // one
                        val x = 1 // two
                        /* three */ val y = 2
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder listOf(bodyComment(2, 5), bodyComment(3, 15), bodyComment(4, 5))
        }

        test("comments in lambdas accessors initializers and constructors are flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    val action: () -> Unit = {
                        // lambda
                        run()
                    }

                    class Sample {
                        init {
                            // initializer
                            run()
                        }

                        val value: Int
                            get() {
                                // getter
                                return 1
                            }

                        constructor() {
                            // constructor
                            run()
                        }
                    }

                    fun foo() {
                        listOf(1).forEach { item ->
                            // nested
                            use(item)
                        }
                    }
                    """.trimIndent() + "\n"
                ).diagnostics
                .map(KtLintRuleTestEngine.Diagnostic::line) shouldContainExactlyInAnyOrder listOf(2, 8, 14, 19, 26)
        }

        test("comments outside function bodies are not flagged") {
            val source =
                """
                // file-level comment
                // region helpers
                class Sample {
                    // property note
                    val value: Int = 1 // trailing note

                    /* member block */
                    fun work(
                        // parameter note
                        value: Int
                    ): Int = value
                }
                // endregion
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("kdoc comment is not flagged") {
            val source =
                """
                /**
                 * docs
                 */
                fun foo() {
                    /**
                     * local docs
                     */
                    fun bar() {
                    }
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("formatter directives are allowed in function bodies") {
            val source =
                """
                fun foo() {
                    // @formatter:off
                    val matrix = listOf(1,0, 0,1)
                    // @formatter:on
                    use(matrix)
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("comment markers inside raw string are ignored") {
            val source =
                """
                fun text(): String {
                    return ${"\"\"\""}// not a comment /* also not a comment */${"\"\"\""}
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("format leaves commented source unchanged") {
            val source =
                """
                fun foo() {
                    // comment
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine.execute(ruleProvider, source).formattedCode shouldBe source
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::NoLineComment)

        private fun bodyComment(
            line: Int,
            column: Int
        ): KtLintRuleTestEngine.Diagnostic =
            KtLintRuleTestEngine.Diagnostic(
                line,
                column,
                "harness:no-line-comment",
                canBeAutoCorrected = false
            )
    }
}
