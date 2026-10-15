package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies that function bodies stay free of blank lines while declaration separation stays intact.
 */
class FunctionBodyBlankLinesTest :
    FunSpec({
        test("removes decorative blank lines inside function bodies") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun work() {
                        first()

                        second()
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder listOf(decorativeBlankLine(2, 12))
                formattedCode shouldBe
                    """
                    fun work() {
                        first()
                        second()
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("removes blank lines before local properties and destructuring declarations") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun work(pair: Pair<Int, Int>) {
                        first()

                        val local: Int = 1

                        val (left, right) = pair
                        second(local, left, right)
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder listOf(decorativeBlankLine(2, 12), decorativeBlankLine(4, 23))
                formattedCode shouldBe
                    """
                    fun work(pair: Pair<Int, Int>) {
                        first()
                        val local: Int = 1
                        val (left, right) = pair
                        second(local, left, right)
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("removes blank lines in nested blocks lambdas initializers and accessors") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    class Sample {
                        init {
                            first()

                            second()
                        }

                        val value: Int
                            get() {
                                first()

                                return 1
                            }

                        constructor() {
                            first()

                            second()
                        }

                        fun work(ready: Boolean) {
                            if (ready) {
                                first()

                                second()
                            }
                            listOf(1).forEach { item ->
                                first()

                                second(item)
                            }
                        }
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics.map(KtLintRuleTestEngine.Diagnostic::line) shouldContainExactlyInAnyOrder listOf(3, 10, 16, 23, 28)
                formattedCode shouldBe
                    """
                    class Sample {
                        init {
                            first()
                            second()
                        }

                        val value: Int
                            get() {
                                first()
                                return 1
                            }

                        constructor() {
                            first()
                            second()
                        }

                        fun work(ready: Boolean) {
                            if (ready) {
                                first()
                                second()
                            }
                            listOf(1).forEach { item ->
                                first()
                                second(item)
                            }
                        }
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("preserves blank lines around local functions classes comments and annotations") {
            val source =
                """
                fun work() {
                    first()

                    fun helper() {
                    }

                    class Local

                    /**
                     * Documents the call.
                     */
                    second()
                    third()

                    // @formatter:off
                    fourth()
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("preserves blank lines used by tool directives") {
            val source =
                """
                fun work() {
                    first()

                    // @formatter:off
                    second()
                    // @formatter:on

                    third()
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("removes blank lines in property lambdas while preserving class body spacing") {
            val source =
                """
                class Sample {
                    val first: Int = 1

                    val second: Int = 2
                }

                val action: () -> Unit = {
                    first()

                    second()
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder listOf(decorativeBlankLine(8, 12))
                formattedCode shouldBe
                    """
                    class Sample {
                        val first: Int = 1

                        val second: Int = 2
                    }

                    val action: () -> Unit = {
                        first()
                        second()
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("cleans executable Kotest bodies without changing registration spacing") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import io.kotest.core.spec.style.FunSpec

                    class SampleTest : FunSpec({
                        beforeEach {
                            first()

                            second()
                        }

                        context("group") {
                            test("first") {
                                first()

                                second()
                            }

                            test("second") {}
                        }
                    })
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics.map(KtLintRuleTestEngine.Diagnostic::line) shouldContainExactlyInAnyOrder listOf(5, 12)
                formattedCode shouldBe
                    """
                    import io.kotest.core.spec.style.FunSpec

                    class SampleTest : FunSpec({
                        beforeEach {
                            first()
                            second()
                        }

                        context("group") {
                            test("first") {
                                first()
                                second()
                            }

                            test("second") {}
                        }
                    })
                    """.trimIndent() + "\n"
            }
        }

        test("leaves Kotest registration scopes to the sibling spacing rule") {
            val source =
                """
                import io.kotest.core.spec.style.FunSpec

                class SampleTest : FunSpec() {
                    init {
                        val shared: Int = 1

                        test("first") {
                            run()
                        }

                        test("second") {
                            run()
                        }
                    }
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::FunctionBodyBlankLines)

        private fun decorativeBlankLine(
            line: Int,
            column: Int
        ): KtLintRuleTestEngine.Diagnostic =
            KtLintRuleTestEngine.Diagnostic(
                line,
                column,
                "harness:function-body-blank-lines",
                canBeAutoCorrected = true
            )
    }
}
