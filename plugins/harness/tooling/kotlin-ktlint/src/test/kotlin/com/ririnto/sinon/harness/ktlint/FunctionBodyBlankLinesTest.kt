package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class FunctionBodyBlankLinesTest :
    FunSpec({
        test("removes decorative blank lines inside function bodies") {
            val source =
                """
                fun work() {
                    first()

                    second()
                }
                """.trimIndent() + "\n"
            val expected =
                """
                fun work() {
                    first()
                    second()
                }
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        12,
                        "remove the decorative blank line from the function body",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("preserves blank lines used by tool directives") {
            val source =
                """
                fun work() {
                    first()

                    second()
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        12,
                        "remove the decorative blank line from the function body",
                        canBeAutoCorrected = true
                    )
                )
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::FunctionBodyBlankLines)
    }
}
