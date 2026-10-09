package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class MultilineKdocTest :
    FunSpec({
        test("expands single line kdoc") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    /** Contract. */
                    class Service
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(KtLintRuleTestEngine.Diagnostic(1, 1, "use multiline KDoc for this declaration", canBeAutoCorrected = true))
                formattedCode shouldBe
                    """
                    /**
                     * Contract.
                     */
                    class Service
                    """.trimIndent() + "\n"
            }
        }

        test("preserves already multiline kdoc") {
            val source =
                """
                /**
                 * Contract.
                 */
                class Service
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("expands an opening delimiter beside documented text") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    /** Contract.
                     * More detail.
                     */
                    class Service
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(KtLintRuleTestEngine.Diagnostic(1, 1, "use multiline KDoc for this declaration", canBeAutoCorrected = true))
                formattedCode shouldBe
                    """
                    /**
                     * Contract.
                     * More detail.
                     */
                    class Service
                    """.trimIndent() + "\n"
            }
        }

        test("expands a closing delimiter beside documented text") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    /**
                     * Contract.
                     * More detail. */
                    class Service
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(KtLintRuleTestEngine.Diagnostic(1, 1, "use multiline KDoc for this declaration", canBeAutoCorrected = true))
                formattedCode shouldBe
                    """
                    /**
                     * Contract.
                     * More detail.
                     */
                    class Service
                    """.trimIndent() + "\n"
            }
        }

        test("preserves paragraph breaks and code indentation when expanding delimiters") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    /** Contract.
                     *
                     *     example()
                     * @return the result */
                    class Service
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(KtLintRuleTestEngine.Diagnostic(1, 1, "use multiline KDoc for this declaration", canBeAutoCorrected = true))
                formattedCode shouldBe
                    """
                    /**
                     * Contract.
                     *
                     *     example()
                     * @return the result
                     */
                    class Service
                    """.trimIndent() + "\n"
            }
        }

        test("preserves first line code indentation when expanding delimiters") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    /**     example()
                     *     more() */
                    class Service
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(KtLintRuleTestEngine.Diagnostic(1, 1, "use multiline KDoc for this declaration", canBeAutoCorrected = true))
                formattedCode shouldBe
                    """
                    /**
                     *     example()
                     *     more()
                     */
                    class Service
                    """.trimIndent() + "\n"
            }
        }

        test("reports first line tab indentation without changing its content") {
            listOf("\t", " \t", "    \t").forEach { indentation ->
                val source =
                    """
                    /**${indentation}example()
                     * more()
                     */
                    class Service
                    """.trimIndent() + "\n"
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    diagnostics shouldContainExactlyInAnyOrder
                        listOf(KtLintRuleTestEngine.Diagnostic(1, 1, "use multiline KDoc for this declaration", false))
                    formattedCode shouldBe source
                }
            }
        }

        test("reports code beside the closing delimiter without moving it") {
            val source =
                """
                /**
                 * Contract.
                 */ class Service
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(KtLintRuleTestEngine.Diagnostic(1, 1, "put KDoc delimiters on separate source lines", false))
                formattedCode shouldBe source
            }
        }

        test("reports code beside the opening delimiter without moving it") {
            val source =
                """
                class Earlier; /**
                 * Contract.
                 */
                class Service
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(KtLintRuleTestEngine.Diagnostic(1, 16, "put KDoc delimiters on separate source lines", false))
                formattedCode shouldBe source
            }
        }

        test("preserves single line Markdown emphasis") {
            listOf("*Important* contract.", "**Bold** contract.").forEach { text ->
                assertSoftly(
                    KtLintRuleTestEngine.execute(
                        ruleProvider,
                        """
                        /** $text */
                        class Service
                        """.trimIndent() + "\n"
                    )
                ) {
                    diagnostics shouldContainExactlyInAnyOrder
                        listOf(KtLintRuleTestEngine.Diagnostic(1, 1, "use multiline KDoc for this declaration", true))
                    formattedCode shouldBe
                        """
                        /**
                         * $text
                         */
                        class Service
                        """.trimIndent() + "\n"
                }
            }
        }

        test("reports undecorated indented Markdown without changing its content") {
            val source =
                """
                /** Contract.

                    example()
                 */
                class Service
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(KtLintRuleTestEngine.Diagnostic(1, 1, "use multiline KDoc for this declaration", false))
                formattedCode shouldBe source
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::MultilineKdoc)
    }
}
