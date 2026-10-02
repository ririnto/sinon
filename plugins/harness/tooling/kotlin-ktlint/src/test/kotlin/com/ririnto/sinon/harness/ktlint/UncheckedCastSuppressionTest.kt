package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class UncheckedCastSuppressionTest :
    FunSpec({
        test("stale suppress on function without cast autocorrects by removing annotation") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "@Suppress(\"UNCHECKED_CAST\")\nfun sample(): Int = 42\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        1,
                        "avoid suppression of forbidden tokens (`@Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "fun sample(): Int = 42\n"
        }

        test("stale suppress on property initializer autocorrects") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "@Suppress(\"UNCHECKED_CAST\")\nval sample: Int = 42\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        1,
                        "avoid suppression of forbidden tokens (`@Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "val sample: Int = 42\n"
        }

        test("stale file level suppress autocorrects and removes file annotation line") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "@file:Suppress(\"UNCHECKED_CAST\")\n\npackage com.example\n\nfun sample(): Int = 42\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        1,
                        "avoid suppression of forbidden tokens (`@file:Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "package com.example\n\nfun sample(): Int = 42\n"
        }

        test("stale suppress among other annotations removes only the suppress entry") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "@Deprecated(\"old\")\n@Suppress(\"UNCHECKED_CAST\")\nfun sample(): Int = 42\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        1,
                        "avoid suppression of forbidden tokens (`@Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "@Deprecated(\"old\")\n\nfun sample(): Int = 42\n"
        }

        test("live suppress on function with as cast remains uncorrected") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "@Suppress(\"UNCHECKED_CAST\")\nfun sample(value: Any): String = value as String\n"
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
                    "@Suppress(\"UNCHECKED_CAST\", \"DEPRECATION\")\nfun sample(): Int = 42\n"
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
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "@Suppress(\"UNCHECKED_CAST\")\nfun sample(): Int = 42\n",
                    editorConfigOverride = EditorConfigOverride.from(UncheckedCastSuppression.ALLOWED_SUPPRESSIONS to "UNCHECKED_CAST")
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "@Suppress(\"UNCHECKED_CAST\")\nfun sample(): Int = 42\n"
        }

        test("custom forbidden token detected and autocorrects when stale") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "@Suppress(\"USELESS_CAST\")\nfun sample(): Int = 42\n",
                    editorConfigOverride = EditorConfigOverride.from(UncheckedCastSuppression.FORBIDDEN_SUPPRESSIONS to "USELESS_CAST")
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        1,
                        "avoid suppression of forbidden tokens (`@Suppress(\"USELESS_CAST\")`); refactor to type-safe cast or explicit handling",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "fun sample(): Int = 42\n"
        }

        test("non suppress annotation is ignored") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "@Deprecated(\"use newSample instead\")\nfun sample(): Int = 42\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "@Deprecated(\"use newSample instead\")\nfun sample(): Int = 42\n"
        }

        test("fully qualified suppress annotation is detected") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "@kotlin.Suppress(\"UNCHECKED_CAST\")\nfun sample(): Int = 42\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        1,
                        "avoid suppression of forbidden tokens (`@kotlin.Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "fun sample(): Int = 42\n"
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::UncheckedCastSuppression)
    }
}
