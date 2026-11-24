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
        test("preserves modified-file notices in KDoc") {
            val source =
                """
                /**
                 * Flags companion objects that are not the first non-enum-entry declaration in their class or object body.
                 *
                 * Modified for the local package and rule ID.
                 */
                class Example
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics.shouldContainExactlyInAnyOrder(emptyList())
                formattedCode shouldBe source
            }
        }

        test("required license and suppression directives are preserved") {
            val source = "fun work() {\n/* @license MIT */\n// noinspection UnusedVariable\nval value = 1\n}\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
        }

        test("legal markers inside explanatory comments do not exempt them") {
            val source =
                """
                fun work() {
                    // This comment discusses copyright.
                    // The @license marker appears here as prose.
                    /* This text mentions SPDX-License-Identifier: without declaring one. */
                    // The value was modified by a caller.
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(listOf(comment(2, 5), comment(3, 5), comment(4, 5), comment(5, 5)))
                }
                formattedCode shouldBe source
            }
        }

        test("preserves a modified-file notice inside a copyright and license header") {
            val source =
                """
                /*
                 * Copyright (c) 2026 Example contributors
                 * SPDX-License-Identifier: Apache-2.0
                 * This file was modified to support local configuration.
                 */
                fun work() {}
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
        }

        test("reports unmarked provenance for review and preserves the source") {
            val source =
                """
                // This file was modified to support local configuration.
                /* Modified for the local package and rule ID. */
                class Example
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics.shouldContainExactlyInAnyOrder(listOf(comment(1, 1), comment(2, 1)))
                diagnostics.map(KtLintRuleTestEngine.Diagnostic::detail) shouldBe
                    List(2) { "Keep required notices. Put discretionary rationale in KDoc on the declaration." }
                formattedCode shouldBe source
            }
        }

        context("a modification notice before the copyright in an Apache 2.0 header") {
            val header =
                """
                /*
                 * This file was modified to support local configuration.
                 *
                 * Original license terms:
                 *
                 * Copyright 2026 Example contributors
                 *
                 * Licensed under the Apache License, Version 2.0 (the "License");
                 */
                """.trimIndent()

            test("preserves the opening block and still reports a neighboring provenance comment") {
                val source = "$header\n// This file was modified to support local configuration.\npackage example\n"
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    diagnostics.shouldContainExactlyInAnyOrder(listOf(comment(10, 1)))
                    formattedCode shouldBe source
                }
            }

            test("preserves the opening block after a script shebang") {
                val source = "#!/usr/bin/env kotlin\n$header\nprintln(42)\n"
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source, isKotlinScript = true)) {
                    diagnostics.shouldContainExactlyInAnyOrder(emptyList())
                    formattedCode shouldBe source
                }
            }

            test("reports the same block below the package declaration") {
                val source = "package example\n$header\nclass Example\n"
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    diagnostics.shouldContainExactlyInAnyOrder(listOf(comment(2, 1)))
                    formattedCode shouldBe source
                }
            }

            test("reports a leading block that lacks the Apache license declaration") {
                val source = "$header\nclass Example\n".replace("Licensed under the Apache License,", "See the Apache License,")
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    diagnostics.shouldContainExactlyInAnyOrder(listOf(comment(1, 1)))
                    formattedCode shouldBe source
                }
            }

            test("reports a leading block that lacks a copyright notice") {
                val source = "$header\nclass Example\n".replace("Copyright 2026 Example contributors", "Copyright is documented elsewhere.")
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    diagnostics.shouldContainExactlyInAnyOrder(listOf(comment(1, 1)))
                    formattedCode shouldBe source
                }
            }

            test("reports a comment that quotes the entire header in a nested block") {
                val source = "/* A former header is shown here.\n$header\n*/\nclass Example\n"
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    diagnostics.shouldContainExactlyInAnyOrder(listOf(comment(1, 1)))
                    formattedCode shouldBe source
                }
            }
        }

        test("leading markers in bang comments preserve license notices") {
            val source =
                """
                /*! @license MIT */
                /*! Copyright 2026 Sinon contributors. */
                fun work() {}
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
        }

        test("reports a standalone line comment in a function body and preserves source") {
            val source =
                """
                fun foo() {
                    // comment
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics.shouldContainExactlyInAnyOrder(listOf(comment(2, 5)))
                formattedCode shouldBe source
            }
        }

        test("trailing line comment in a function body is flagged") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun foo() {
                        val x = 1 // comment
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(listOf(comment(2, 15)))
                }
            }
        }

        test("block comment in a function body is flagged") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun foo() {
                        /* block */
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(listOf(comment(2, 5)))
                }
            }
        }

        test("mixed comments in a function body are all flagged") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun foo() {
                        // one
                        val x = 1 // two
                        /* three */ val y = 2
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(listOf(comment(2, 5), comment(3, 15), comment(4, 5)))
                }
            }
        }

        test("comments in lambdas accessors initializers and constructors are flagged") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
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
                )
            ) {
                assertSoftly(diagnostics) {
                    map(KtLintRuleTestEngine.Diagnostic::line) shouldContainExactlyInAnyOrder listOf(2, 8, 14, 19, 26)
                }
            }
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
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
                    )
                }
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(listOf(comment(3, 5), comment(5, 5)))
                }
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(listOf(comment(2, 5)))
                }
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
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
