package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class RegexConstructorTest :
    FunSpec({
        test("autocorrects one positional string template expression") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    val pattern = Regex("a+")
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            15,
                            "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    val pattern = "a+".toRegex()
                    """.trimIndent() + "\n"
            }
        }

        test("autocorrects regex constructor used as receiver") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    val matched = Regex("a+").matches("aaa")
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            15,
                            "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    val matched = "a+".toRegex().matches("aaa")
                    """.trimIndent() + "\n"
            }
        }

        test("autocorrects regex constructor used as argument") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    val result = listOf(Regex("a+"))
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            21,
                            "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    val result = listOf("a+".toRegex())
                    """.trimIndent() + "\n"
            }
        }

        test("leaves unsafe constructor shapes unchanged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun build(pattern: String, option: RegexOption): Regex {
                        val first = Regex(pattern)
                        val second = Regex(pattern = "a+")
                        val third = Regex("a+", option)
                        return Regex(makePattern())
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        17,
                        "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        18,
                        "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        17,
                        "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        5,
                        12,
                        "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("qualified receiver regex is safe") {
            val source =
                """
                class Factory
                fun build(factory: Factory) = factory.Regex("a+")
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("autocorrects fully qualified kotlin text regex call") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    val pattern = kotlin.text.Regex("a+")
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            27,
                            "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    val pattern = "a+".toRegex()
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
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            3,
                            28,
                            "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    import com.example.Regex
                    val suppressed = Regex("a+")
                    val explicit = "a+".toRegex()
                    """.trimIndent() + "\n"
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::RegexConstructor)
    }
}
