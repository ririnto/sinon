package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ControlFlowBracesTest :
    FunSpec({
        test("formats every if branch with braces") {
            val source =
                """
                fun choose(ready: Boolean) {
                    if (ready) work()
                    else stop()
                }
                """.trimIndent() + "\n"
            val expected =
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
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(2, 16, "wrap the `if` branch in `{ ... }`"),
                    KtLintRuleTestEngine.Diagnostic(3, 10, "wrap the `else` branch in `{ ... }`")
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("formats loop bodies without changing the loop") {
            val source =
                """
                fun visit(items: List<Int>, ready: Boolean) {
                    for (item in items) use(item)
                    while (ready) tick()
                    do tick() while (ready)
                }
                """.trimIndent() + "\n"
            val expected =
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
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(2, 25, "wrap the `for` body in `{ ... }`"),
                    KtLintRuleTestEngine.Diagnostic(3, 19, "wrap the `while` body in `{ ... }`"),
                    KtLintRuleTestEngine.Diagnostic(4, 8, "wrap the `do-while` body in `{ ... }`")
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("leaves multiline raw string bodies unchanged") {
            val source = "fun print(ready: Boolean) {\n    if (ready) println(\"\"\"\nvalue\n\"\"\")\n}\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(KtLintRuleTestEngine.Diagnostic(2, 16, "wrap the `if` branch in `{ ... }`", canBeAutoCorrected = false))
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::ControlFlowBraces)
    }
}
