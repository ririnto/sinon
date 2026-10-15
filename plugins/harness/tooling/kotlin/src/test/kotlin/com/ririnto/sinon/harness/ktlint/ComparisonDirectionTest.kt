package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies the comparison direction rule.
 */
class ComparisonDirectionTest :
    FunSpec({
        test("reports but never autocorrects") {
            KtLintRuleTestEngine.execute(ruleProvider, "fun compare(a: Int, b: Int) = a > b\n").diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        33,
                        "harness:comparison-direction",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("reports greater than or equal") {
            KtLintRuleTestEngine.execute(ruleProvider, "fun compare(a: Int, b: Int) = a >= b\n").diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        33,
                        "harness:comparison-direction",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("leaves less than operators unflagged") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun compare(a: Int, b: Int) = a < b\n")) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe "fun compare(a: Int, b: Int) = a < b\n"
            }
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun compare(a: Int, b: Int) = a <= b\n")) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe "fun compare(a: Int, b: Int) = a <= b\n"
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::ComparisonDirection)
    }
}
