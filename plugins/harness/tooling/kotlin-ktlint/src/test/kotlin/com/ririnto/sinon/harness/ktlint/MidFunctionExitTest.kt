package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class MidFunctionExitTest :
    FunSpec({
        test("flags function return before later statements") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun handle(ready: Boolean) {
                        prepare()
                        if (ready) return
                        process()
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        5,
                        "Avoid a mid-block `if` with `return`. Invert the condition or use `?.let` only for optional work when return semantics stay unchanged",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("flags if branches that return in either branch before later statements") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun first(ready: Boolean) {
                        if (ready) return else process()
                        finish()
                    }

                    fun second(ready: Boolean) {
                        if (ready) process() else return
                        finish()
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        5,
                        "Avoid a mid-block `if` with `return`. Invert the condition or use `?.let` only for optional work when return semantics stay unchanged",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        7,
                        5,
                        "Avoid a mid-block `if` with `return`. Invert the condition or use `?.let` only for optional work when return semantics stay unchanged",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("flags break and continue only inside loop bodies with later statements") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun process(items: List<Int>) {
                        for (item in items) {
                            if (item < 0) continue
                            if (item == 0) break
                            use(item)
                        }
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        9,
                        "Avoid a mid-block `if` with `continue`. Invert the condition and keep the main path clear when behavior stays unchanged",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        9,
                        "Avoid a mid-block `if` with `break`. Move the condition into the loop or invert it when behavior stays unchanged",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("ignores terminal returns lambdas labeled exits and value if expressions") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun terminal(done: Boolean) {
                        if (done) return
                    }

                    fun process(items: List<Int>) {
                        items.forEach { item ->
                            if (item < 0) return@forEach
                            val label = if (item == 0) "zero" else "other"
                            use(label)
                        }
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe
                    """
                    fun terminal(done: Boolean) {
                        if (done) return
                    }

                    fun process(items: List<Int>) {
                        items.forEach { item ->
                            if (item < 0) return@forEach
                            val label = if (item == 0) "zero" else "other"
                            use(label)
                        }
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("ignores unlabeled returns inside lambda execution boundaries") {
            val source =
                """
                fun process(items: List<Int>) {
                    items.forEach { item ->
                        if (item < 0) return
                        consume(item)
                    }
                    finish()
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("ignores if without an exit branch") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun process(enabled: Boolean) {
                        if (enabled) prepare()
                        use()
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe
                    """
                    fun process(enabled: Boolean) {
                        if (enabled) prepare()
                        use()
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("detects unlabeled return when its expression contains at sign") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun render(enabled: Boolean): String {
                        if (enabled) return "@"
                        return "done"
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        5,
                        "Avoid a mid-block `if` with `return`. Invert the condition or use `?.let` only for optional work when return semantics stay unchanged",
                        canBeAutoCorrected = false
                    )
                )
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::MidFunctionExit)
    }
}
