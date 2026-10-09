package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class NullComparisonIdentityTest :
    FunSpec({
        test("flags structural null comparisons on either side including parentheses") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun inspect(value: Any?) {
                        value == null
                        null != value
                        (value) == ((null))
                        ((null)) != (value)
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(2, 5, "use `===` or `!==` for comparisons with null", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(3, 5, "use `===` or `!==` for comparisons with null", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(4, 5, "use `===` or `!==` for comparisons with null", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(5, 5, "use `===` or `!==` for comparisons with null", canBeAutoCorrected = false)
                )
        }

        test("allows identity comparisons and structural comparisons without null literal") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun inspect(value: Any?) {
                        value === null
                        null !== value
                        value == "null"
                        value != "other"
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe
                    """
                    fun inspect(value: Any?) {
                        value === null
                        null !== value
                        value == "null"
                        value != "other"
                    }
                    """.trimIndent() + "\n"
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::NullComparisonIdentity)
    }
}
