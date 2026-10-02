package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class MultilineKdocTest :
    FunSpec({
        test("expands single line kdoc") {
            val source = "/** Contract. */\nclass Service\n"
            val expected =
                """
                /**
                 * Contract.
                 */
                class Service
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(KtLintRuleTestEngine.Diagnostic(1, 1, "use multiline KDoc for this declaration", canBeAutoCorrected = true))
            lintResult1.formattedCode shouldBe expected
        }

        test("preserves already multiline kdoc") {
            val source =
                """
                /**
                 * Contract.
                 */
                class Service
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::MultilineKdoc)
    }
}
