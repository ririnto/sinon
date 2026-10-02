package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class NoLineCommentTest :
    FunSpec({
        test("standalone line comment is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun foo() {
                        // comment
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        5,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("trailing line comment is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun foo() {
                        val x = 1 // comment
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        15,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("block comment is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun foo() {
                        /* block */
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        5,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("mixed line and block comments are all flagged") {
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
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        5,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        15,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        5,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("kdoc comment is not flagged") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    /**
                     * docs
                     */
                    fun foo()
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe
                    """
                    /**
                     * docs
                     */
                    fun foo()
                    """.trimIndent() + "\n"
            }
        }

        test("comment at top of file is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    // file-level comment
                    fun foo()
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        1,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("line comment inside lambda body is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun foo() {
                        listOf(1).forEach {
                            // comment
                            it.inc()
                        }
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        9,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("comment markers inside raw string are ignored") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    val text = ${"\"\"\""}// not a comment /* also not a comment */${"\"\"\""}
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe
                    """
                    val text = ${"\"\"\""}// not a comment /* also not a comment */${"\"\"\""}
                    """.trimIndent() + "\n"
            }
        }

        test("format leaves commented source unchanged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun foo() {
                        // comment
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        5,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    )
                )
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::NoLineComment)
    }
}
