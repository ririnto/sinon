package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies the regex constructor rule.
 */
class RegexConstructorTest :
    FunSpec({
        test("keeps a positional string template constructor manual") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    val pattern = Regex("a+")
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                15,
                                "harness:no-regex-constructor",
                                canBeAutoCorrected = false
                            )
                        )
                    )
                }
                formattedCode shouldBe
                    """
                    val pattern = Regex("a+")
                    """.trimIndent() + "\n"
            }
        }

        test("keeps a regex constructor used as receiver manual") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    val matched = Regex("a+").matches("aaa")
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                15,
                                "harness:no-regex-constructor",
                                canBeAutoCorrected = false
                            )
                        )
                    )
                }
                formattedCode shouldBe
                    """
                    val matched = Regex("a+").matches("aaa")
                    """.trimIndent() + "\n"
            }
        }

        test("keeps a regex constructor used as argument manual") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    val result = listOf(Regex("a+"))
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                21,
                                "harness:no-regex-constructor",
                                canBeAutoCorrected = false
                            )
                        )
                    )
                }
                formattedCode shouldBe
                    """
                    val result = listOf(Regex("a+"))
                    """.trimIndent() + "\n"
            }
        }

        test("leaves unsafe constructor shapes unchanged") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun build(pattern: String, option: RegexOption): Regex {
                        val first = Regex(pattern)
                        val second = Regex(pattern = "a+")
                        val third = Regex("a+", option)
                        return Regex(makePattern())
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                2,
                                17,
                                "harness:no-regex-constructor",
                                canBeAutoCorrected = false
                            ),
                            KtLintRuleTestEngine.Diagnostic(
                                3,
                                18,
                                "harness:no-regex-constructor",
                                canBeAutoCorrected = false
                            ),
                            KtLintRuleTestEngine.Diagnostic(
                                4,
                                17,
                                "harness:no-regex-constructor",
                                canBeAutoCorrected = false
                            ),
                            KtLintRuleTestEngine.Diagnostic(
                                5,
                                12,
                                "harness:no-regex-constructor",
                                canBeAutoCorrected = false
                            )
                        )
                    )
                }
            }
        }

        test("qualified receiver regex is safe") {
            val source =
                """
                class Factory
                fun build(factory: Factory) = factory.Regex("a+")
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
        }

        test("keeps fully qualified kotlin text regex call manual") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    val pattern = kotlin.text.Regex("a+")
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                27,
                                "harness:no-regex-constructor",
                                canBeAutoCorrected = false
                            )
                        )
                    )
                }
                formattedCode shouldBe
                    """
                    val pattern = kotlin.text.Regex("a+")
                    """.trimIndent() + "\n"
            }
        }

        test("explicit qualification bypasses name conflict suppression") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import com.example.Regex
                    val suppressed = Regex("a+")
                    val explicit = kotlin.text.Regex("a+")
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                3,
                                28,
                                "harness:no-regex-constructor",
                                canBeAutoCorrected = false
                            )
                        )
                    )
                }
                formattedCode shouldBe
                    """
                    import com.example.Regex
                    val suppressed = Regex("a+")
                    val explicit = kotlin.text.Regex("a+")
                    """.trimIndent() + "\n"
            }
        }

        test("keeps the constructor when a local toRegex extension shadows the standard one") {
            val source =
                """
                fun main() {
                    fun String.toRegex(): String = "shadow"
                    val pattern = kotlin.text.Regex("a+")
                    check(pattern.pattern == "a+")
                    check(pattern.matches("aaa"))
                    check(!pattern.matches("bbb"))
                }
                """.trimIndent() + "\n"
            assertManualConstructorIsStable(source)
        }

        test("keeps the constructor when a private top level toRegex extension shadows the standard one") {
            val source =
                """
                private fun String.toRegex(): String = "shadow"
                fun main() {
                    val pattern = kotlin.text.Regex("a+")
                    check(pattern.pattern == "a+")
                    check(pattern.matches("aaa"))
                    check(!pattern.matches("bbb"))
                }
                """.trimIndent() + "\n"
            assertManualConstructorIsStable(source)
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::RegexConstructor)

        private fun assertManualConstructorIsStable(source: String) {
            val result = KtLintRuleTestEngine.execute(ruleProvider, source)
            val constructorOffset = source.indexOf("kotlin.text.Regex") + "kotlin.text.".length
            val line = source.take(constructorOffset).count { character -> character == '\n' } + 1
            val column = constructorOffset - source.lastIndexOf('\n', constructorOffset - 1)
            assertSoftly(result) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                line,
                                column,
                                "harness:no-regex-constructor",
                                canBeAutoCorrected = false
                            )
                        )
                    )
                }
                formattedCode shouldBe source
            }
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, result.formattedCode)) {
                formattedCode shouldBe source
            }
        }
    }
}
