package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ImplicitLambdaItTest :
    FunSpec({
        test("names implicit parameter and preserves escapes") {
            val source =
                """
                fun render(items: List<String>) = items.joinToString { "\\n${'$'}it" }
                """.trimIndent() + "\n"
            val expected =
                """
                fun render(items: List<String>) = items.joinToString { value -> "\\n${'$'}value" }
                """.trimIndent() + "\n"
            val itOffset = source.indexOf("it", source.indexOf('{'))
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        itOffset + 1,
                        "use an explicit name for the implicit `it` lambda parameter",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("preserves outer implicit capture inside nested explicit lambda") {
            val source =
                """
                fun nested(values: List<String>) = values.map { it.let { item -> it + item } }
                """.trimIndent() + "\n"
            val expected =
                """
                fun nested(values: List<String>) = values.map { value -> value.let { item -> value + item } }
                """.trimIndent() + "\n"
            val itOffset = source.indexOf("it", source.indexOf('{'))
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        itOffset + 1,
                        "use an explicit name for the implicit `it` lambda parameter",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("reports only the lambda with an implicit parameter") {
            val source =
                """
                fun nested(values: List<List<Int>>) = values.map {
                    it.flatMap { inner -> inner + it.size }
                }
                """.trimIndent() + "\n"
            val itOffset = source.indexOf("it", source.indexOf('{'))
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        itOffset - source.indexOf('\n'),
                        "use an explicit name for the implicit `it` lambda parameter",
                        canBeAutoCorrected = true
                    )
                )
        }

        test("preserves nested implicit shadowing") {
            val source =
                """
                fun nested(values: List<List<Int>>) = values.map { it.map { it + 1 } }
                """.trimIndent() + "\n"
            val expected =
                """
                fun nested(values: List<List<Int>>) = values.map { valueValue -> valueValue.map { value -> value + 1 } }
                """.trimIndent() + "\n"
            val itOffset = source.indexOf("it", source.indexOf('{'))
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(1, itOffset + 1, "use an explicit name for the implicit `it` lambda parameter", true),
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        source.lastIndexOf("it") + 1,
                        "use an explicit name for the implicit `it` lambda parameter",
                        true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("preserves multiline escaped string template references") {
            val source =
                """
                fun render(items: List<String>) = items.joinToString {
                    "\\n${'$'}it"
                }
                """.trimIndent() + "\n"
            val expected =
                """
                fun render(items: List<String>) = items.joinToString {
                    value ->
                    "\\n${'$'}value"
                }
                """.trimIndent() + "\n"
            val itOffset = source.indexOf("it", source.indexOf('{'))
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        itOffset - source.indexOf('\n'),
                        "use an explicit name for the implicit `it` lambda parameter",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("preserves raw string template references") {
            val rawQuote = "\"\"\""
            val source =
                """
                fun render(items: List<String>) = items.joinToString { $rawQuote${'$'}it$rawQuote }
                """.trimIndent() + "\n"
            val expected =
                """
                fun render(items: List<String>) = items.joinToString { value -> $rawQuote${'$'}value$rawQuote }
                """.trimIndent() + "\n"
            val itOffset = source.indexOf("it", source.indexOf('{'))
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        itOffset + 1,
                        "use an explicit name for the implicit `it` lambda parameter",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("does not autocorrect when parameter name would capture an existing reference") {
            val source =
                """
                fun render(items: List<String>, value: String) = items.joinToString { value + it }
                """.trimIndent() + "\n"
            val itOffset = source.lastIndexOf("it")
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        itOffset + 1,
                        "use an explicit name for the implicit `it` lambda parameter",
                        canBeAutoCorrected = true
                    )
                )
        }

        test("accepts named parameters and nested triple quote limitations") {
            val source =
                """
                fun render(items: List<String>) = items.joinToString { value -> value }
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
        private val ruleProvider: RuleProvider = RuleProvider(::ImplicitLambdaIt)
    }
}
