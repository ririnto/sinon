package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class RegexConstructorTest :
    FunSpec({
        test("autocorrects one positional string template expression") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "val pattern = Regex(\"a+\")\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        15,
                        "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "val pattern = \"a+\".toRegex()\n"
        }

        test("autocorrects regex constructor used as receiver") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "val matched = Regex(\"a+\").matches(\"aaa\")\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        15,
                        "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "val matched = \"a+\".toRegex().matches(\"aaa\")\n"
        }

        test("autocorrects regex constructor used as argument") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "val result = listOf(Regex(\"a+\"))\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        21,
                        "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "val result = listOf(\"a+\".toRegex())\n"
        }

        test("leaves unsafe constructor shapes unchanged") {
            val source =
                """
                fun build(pattern: String, option: RegexOption): Regex {
                    val first = Regex(pattern)
                    val second = Regex(pattern = "a+")
                    val third = Regex("a+", option)
                    return Regex(makePattern())
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
                        17,
                        "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        18,
                        "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        17,
                        "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        5,
                        12,
                        "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("qualified receiver regex is safe") {
            val source = "class Factory\nfun build(factory: Factory) = factory.Regex(\"a+\")\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("autocorrects fully qualified kotlin text regex call") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "val pattern = kotlin.text.Regex(\"a+\")\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        27,
                        "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "val pattern = \"a+\".toRegex()\n"
        }

        test("explicit qualification bypasses name conflict suppression") {
            val source =
                """
                import com.example.Regex
                val suppressed = Regex("a+")
                val explicit = kotlin.text.Regex("a+")
                """.trimIndent() + "\n"
            val expected =
                """
                import com.example.Regex
                val suppressed = Regex("a+")
                val explicit = "a+".toRegex()
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        28,
                        "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::RegexConstructor)
    }
}
