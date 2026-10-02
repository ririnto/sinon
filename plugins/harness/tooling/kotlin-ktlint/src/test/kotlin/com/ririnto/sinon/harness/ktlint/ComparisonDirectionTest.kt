package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ComparisonDirectionTest :
    FunSpec({
        test("reports but never autocorrects") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "fun compare(a: Int, b: Int) = a > b\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        33,
                        "avoid `>` in comparisons; prefer `<` with operands swapped",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("reports greater than or equal") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "fun compare(a: Int, b: Int) = a >= b\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        33,
                        "avoid `>=` in comparisons; prefer `<=` with operands swapped",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("leaves less than operators unflagged") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun compare(a: Int, b: Int) = a < b\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "fun compare(a: Int, b: Int) = a < b\n"
            val lintResult2 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun compare(a: Int, b: Int) = a <= b\n"
                )
            lintResult2.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult2.formattedCode shouldBe "fun compare(a: Int, b: Int) = a <= b\n"
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::ComparisonDirection)
    }
}
