package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class TerminalBranchWhenTest :
    FunSpec({
        test("reports only outermost same subject equality chain with final else") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun sample(kind: String) {
                        if (kind == "first") {
                            work()
                        } else if (kind == "second") {
                            continueWork()
                        } else {
                            finish()
                        }
                        if (kind == "first") work()
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        5,
                        "if/else chain compares one subject; use `when (subject)`",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("ignores if without final else") {
            val source =
                """
                fun sample(first: Boolean, second: Boolean) {
                    if (first) work()
                    if (first) work() else if (second) continueWork()
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("reports same subject type checks") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun render(value: Any) {
                        if (value is String) renderText(value)
                        else if (value is Number) renderNumber(value)
                        else renderOther(value)
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        5,
                        "if/else chain compares one subject; use `when (subject)`",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("preserves arbitrary boolean and negated conditions") {
            val source =
                """
                fun sample(first: Boolean, second: Boolean, kind: String) {
                    if (first) work() else if (second) continueWork() else finish()
                    if (kind != "first") work() else if (kind != "second") continueWork() else finish()
                    if (kind == "first") work() else if (other() == kind) continueWork() else finish()
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("ignores chains with an assigned destructured parameter name") {
            val source =
                """
                fun sample(kind: String) {
                    if (true) {
                        var (kind, ignored) = pair()
                        if (kind == "first") work()
                        else if (kind == "second") continueWork()
                        else finish()
                    }
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::TerminalBranchWhen)
    }
}
