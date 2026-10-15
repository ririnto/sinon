package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies the control flow braces rule.
 */
class ControlFlowBracesTest :
    FunSpec({
        test("formats every if branch with braces") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun choose(ready: Boolean) {
                        if (ready) work()
                        else stop()
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(2, 16, "harness:control-flow-braces"),
                        KtLintRuleTestEngine.Diagnostic(3, 10, "harness:control-flow-braces")
                    )
                formattedCode shouldBe
                    """
                    fun choose(ready: Boolean) {
                        if (ready) {
                            work()
                        }
                        else {
                            stop()
                        }
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("formats loop bodies without changing the loop") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun visit(items: List<Int>, ready: Boolean) {
                        for (item in items) use(item)
                        while (ready) tick()
                        do tick() while (ready)
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(2, 25, "harness:control-flow-braces"),
                        KtLintRuleTestEngine.Diagnostic(3, 19, "harness:control-flow-braces"),
                        KtLintRuleTestEngine.Diagnostic(4, 8, "harness:control-flow-braces")
                    )
                formattedCode shouldBe
                    """
                    fun visit(items: List<Int>, ready: Boolean) {
                        for (item in items) {
                            use(item)
                        }
                        while (ready) {
                            tick()
                        }
                        do {
                            tick()
                        } while (ready)
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("wraps zero indent if expressions") {
            listOf(
                "fun choose(ready: Boolean) = if (ready) 1 else 2\n" to
                    "fun choose(ready: Boolean) = if (ready) {\n    1\n} else {\n    2\n}\n",
                "val value = if (ready) 1 else 2\n" to
                    "val value = if (ready) {\n    1\n} else {\n    2\n}\n"
            ).forEach { (source, expected) ->
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    diagnostics.map(KtLintRuleTestEngine.Diagnostic::ruleId) shouldContainExactlyInAnyOrder
                        List(2) { "harness:control-flow-braces" }
                    formattedCode shouldBe expected
                }
            }
        }

        test("leaves multiline raw string bodies unchanged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun print(ready: Boolean) {
                        if (ready) println(${"\"\"\""}
                    value
                    ${"\"\"\""})
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(KtLintRuleTestEngine.Diagnostic(2, 16, "harness:control-flow-braces", canBeAutoCorrected = false))
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::ControlFlowBraces)
    }
}
