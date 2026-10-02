package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class UncheckedCastSuppressionTest :
    FunSpec({
        test("stale suppress on function without cast autocorrects by removing annotation") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    @Suppress("UNCHECKED_CAST")
                    fun sample(): Int = 42
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            1,
                            "avoid suppression of forbidden tokens (`@Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe "fun sample(): Int = 42\n"
            }
        }

        test("stale suppress on property initializer autocorrects") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    @Suppress("UNCHECKED_CAST")
                    val sample: Int = 42
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            1,
                            "avoid suppression of forbidden tokens (`@Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe "val sample: Int = 42\n"
            }
        }

        test("stale file level suppress autocorrects and removes file annotation line") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    @file:Suppress("UNCHECKED_CAST")

                    package com.example

                    fun sample(): Int = 42
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            1,
                            "avoid suppression of forbidden tokens (`@file:Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    package com.example

                    fun sample(): Int = 42
                    """.trimIndent() + "\n"
            }
        }

        test("stale suppress among other annotations removes only the suppress entry") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    @Deprecated("old")
                    @Suppress("UNCHECKED_CAST")
                    fun sample(): Int = 42
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            2,
                            1,
                            "avoid suppression of forbidden tokens (`@Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    @Deprecated("old")

                    fun sample(): Int = 42
                    """.trimIndent() + "\n"
            }
        }

        test("live suppress on function with as cast remains uncorrected") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    @Suppress("UNCHECKED_CAST")
                    fun sample(value: Any): String = value as String
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        1,
                        "avoid suppression of forbidden tokens (`@Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("multi arg suppress with forbidden token remains uncorrected even when stale") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    @Suppress("UNCHECKED_CAST", "DEPRECATION")
                    fun sample(): Int = 42
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        1,
                        "avoid suppression of forbidden tokens (`@Suppress(\"UNCHECKED_CAST\", \"DEPRECATION\")`); refactor to type-safe cast or explicit handling",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("allowed token filter suppresses detection") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    @Suppress("UNCHECKED_CAST")
                    fun sample(): Int = 42
                    """.trimIndent() + "\n",
                    editorConfigOverride = EditorConfigOverride.from(UncheckedCastSuppression.ALLOWED_SUPPRESSIONS to "UNCHECKED_CAST")
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe
                    """
                    @Suppress("UNCHECKED_CAST")
                    fun sample(): Int = 42
                    """.trimIndent() + "\n"
            }
        }

        test("custom forbidden token detected and autocorrects when stale") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    @Suppress("USELESS_CAST")
                    fun sample(): Int = 42
                    """.trimIndent() + "\n",
                    editorConfigOverride = EditorConfigOverride.from(UncheckedCastSuppression.FORBIDDEN_SUPPRESSIONS to "USELESS_CAST")
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            1,
                            "avoid suppression of forbidden tokens (`@Suppress(\"USELESS_CAST\")`); refactor to type-safe cast or explicit handling",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe "fun sample(): Int = 42\n"
            }
        }

        test("non suppress annotation is ignored") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    @Deprecated("use newSample instead")
                    fun sample(): Int = 42
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe
                    """
                    @Deprecated("use newSample instead")
                    fun sample(): Int = 42
                    """.trimIndent() + "\n"
            }
        }

        test("fully qualified suppress annotation is detected") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    @kotlin.Suppress("UNCHECKED_CAST")
                    fun sample(): Int = 42
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            1,
                            "avoid suppression of forbidden tokens (`@kotlin.Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe "fun sample(): Int = 42\n"
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::UncheckedCastSuppression)
    }
}
