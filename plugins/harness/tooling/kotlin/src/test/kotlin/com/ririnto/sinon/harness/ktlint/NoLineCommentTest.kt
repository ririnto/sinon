package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies that ordinary Kotlin comments are rejected throughout source.
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
                ).diagnostics shouldContainExactlyInAnyOrder listOf(comment(2, 5))
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
                ).diagnostics shouldContainExactlyInAnyOrder listOf(comment(2, 15))
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
                ).diagnostics shouldContainExactlyInAnyOrder listOf(comment(2, 5))
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
                ).diagnostics shouldContainExactlyInAnyOrder listOf(comment(2, 5), comment(3, 15), comment(4, 5))
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

        test("file declaration member parameter property and region comments are reported") {
            val source =
                """
                #!/usr/bin/env kotlin
                // file header

                // @license MIT
                // adjacent ordinary comment
                // region declarations
                // declaration comment
                class Sample {
                    // property comment
                    val value: Int = 1 // trailing property comment

                    // member comment
                    /* block member comment */
                    fun work(
                        // parameter comment
                        value: Int
                    ): Int = value
                }
                // endregion
                // file footer
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source, isKotlinScript = true)) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        comment(2, 1),
                        comment(5, 1),
                        comment(6, 1),
                        comment(7, 1),
                        comment(9, 5),
                        comment(10, 24),
                        comment(12, 5),
                        comment(13, 5),
                        comment(15, 9),
                        comment(19, 1),
                        comment(20, 1)
                    )
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

        test("adjacent ordinary comments are reported around required comments") {
            val source =
                """
                fun work() {
                    // @formatter:off
                    // ordinary before suppression
                    // noinspection UnusedVariable
                    // ordinary after suppression
                    run()
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder listOf(comment(3, 5), comment(5, 5))
                formattedCode shouldBe source
            }
        }

        test("nested block comment is reported as one ordinary comment") {
            val source =
                """
                fun work() {
                    /* outer
                       /* nested */
                    */
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder listOf(comment(2, 5))
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

        test("shebang and attached kdoc are outside ordinary comment tokens") {
            val source =
                """
                #!/usr/bin/env kotlin
                /**
                 * Script documentation.
                 */
                class Sample {
                    /**
                     * Property documentation.
                     */
                    val value: Int = 1
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source, isKotlinScript = true)) {
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

        private fun comment(
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
