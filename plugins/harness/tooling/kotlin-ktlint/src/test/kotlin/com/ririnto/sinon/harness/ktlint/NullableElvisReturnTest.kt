package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class NullableElvisReturnTest :
    FunSpec({
        test("map lookup with return fallback is flagged") {
            val source =
                """
                class Example {
                    fun value(values: Map<String, String>, key: String): String {
                        val value = values[key] ?: return "fallback"
                        return value
                    }
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        13,
                        "avoid an Elvis-return property guard; use ?.let for optional work and keep required failure explicit",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("safe call property lookup with return fallback is flagged") {
            val source =
                """
                class Example {
                    fun value(example: Example?): String {
                        val value = example.field ?: return "fallback"
                        return value
                    }

                    val field: String
                        get() = "value"
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        13,
                        "avoid an Elvis-return property guard; use ?.let for optional work and keep required failure explicit",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("bare return after nullable lookup is flagged") {
            val source =
                """
                fun handle(requests: Map<String, String>, key: String) {
                    val request = requests[key] ?: return
                    process(request)
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
                        9,
                        "avoid an Elvis-return property guard; use ?.let for optional work and keep required failure explicit",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("expression body elvis is safe") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "class Example {\n    fun value(value: String?): String = value ?: \"fallback\"\n}\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "class Example {\n    fun value(value: String?): String = value ?: \"fallback\"\n}\n"
        }

        test("elvis without return is safe") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "class Example {\n    val value: String = compute() ?: \"fallback\"\n}\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "class Example {\n    val value: String = compute() ?: \"fallback\"\n}\n"
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::NullableElvisReturn)
    }
}
