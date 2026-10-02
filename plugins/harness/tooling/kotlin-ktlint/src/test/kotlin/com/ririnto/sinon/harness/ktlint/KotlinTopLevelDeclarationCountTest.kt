package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class KotlinTopLevelDeclarationCountTest :
    FunSpec({
        test("allows exactly one top level type declaration") {
            listOf(
                "class Sample",
                "interface Sample",
                "object Sample",
                "enum class Sample { VALUE }",
                "annotation class Sample",
                "typealias Sample = String"
            ).forEach { declarationSource ->
                val lintResult1 =
                    KtLintRuleTestEngine.execute(
                        ruleProvider,
                        "$declarationSource\n"
                    )
                lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
                lintResult1.formattedCode shouldBe "$declarationSource\n"
            }
        }

        test("rejects zero multiple function and property declarations") {
            listOf(
                "",
                "class First\nclass Second\n",
                "fun sample() = 42\n",
                "val sample = 42\n",
                "var sample = 42\n"
            ).forEach { source ->
                KtLintRuleTestEngine
                    .execute(
                        ruleProvider,
                        source
                    ).diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            1,
                            "declare a single top-level type declaration in this file",
                            canBeAutoCorrected = false
                        )
                    )
            }
        }

        test("ignores kotlin scripts") {
            val source =
                """
                val first = 1
                val second = 2
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source,
                    isKotlinScript = true
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::KotlinTopLevelDeclarationCount)
    }
}
