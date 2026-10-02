package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

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
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            2,
                            12,
                            "remove the decorative blank line from the function body",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    fun work() {
                        first()
                        second()
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("preserves blank lines used by tool directives") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun work() {
                        first()

                        second()
                    }
                    """.trimIndent() + "\n"
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
