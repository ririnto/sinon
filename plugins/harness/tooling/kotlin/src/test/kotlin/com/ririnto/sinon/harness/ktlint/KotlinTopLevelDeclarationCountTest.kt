package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies the kotlin top level declaration count rule.
 */
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
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "$declarationSource\n")) {
                    diagnostics shouldContainExactlyInAnyOrder emptyList()
                    formattedCode shouldBe "$declarationSource\n"
                }
            }
        }

        test("rejects zero multiple function and property declarations") {
            listOf(
                """
                class First
                class Second
                """.trimIndent() + "\n",
                "fun sample() = 42\n",
                "val sample = 42\n",
                "var sample = 42\n",
                "private fun helper() = 42\n",
                """
                fun main() {
                }

                fun main(args: Array<String>) {
                }
                """.trimIndent() + "\n"
            ).forEach { source ->
                KtLintRuleTestEngine.execute(ruleProvider, source).diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            1,
                            "harness:kotlin-top-level-declaration-count",
                            canBeAutoCorrected = false
                        )
                    )
            }
        }

        test("allows private top level helpers beside the type declaration") {
            val source =
                """
                class Sample

                private val limit: Int = 42

                private const val NAME: String = "sample"

                private typealias Alias = String

                private fun helper(): Int = limit
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("still counts private top level types and public helpers") {
            listOf(
                "class First\nprivate class Second\n",
                "class First\nfun helper() = 42\n",
                "class First\nval helper = 42\n",
                "class First\ninternal fun helper() = 42\n"
            ).forEach { source ->
                KtLintRuleTestEngine.execute(ruleProvider, source).diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            1,
                            "harness:kotlin-top-level-declaration-count",
                            canBeAutoCorrected = false
                        )
                    )
            }
        }

        test("allows a single main function beside a type or alone") {
            listOf(
                "fun main() {\n}\n",
                "fun main(args: Array<String>) {\n}\n",
                "class Sample\n\nfun main() {\n}\n"
            ).forEach { source ->
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    diagnostics shouldContainExactlyInAnyOrder emptyList()
                    formattedCode shouldBe source
                }
            }
        }

        test("allows files without declarations") {
            listOf(
                "",
                "package sample\n",
                "package sample\n\nimport java.util.UUID\n"
            ).forEach { source ->
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    diagnostics shouldContainExactlyInAnyOrder emptyList()
                    formattedCode shouldBe source
                }
            }
        }

        test("ignores kotlin scripts") {
            val source =
                """
                val first = 1
                val second = 2
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source, isKotlinScript = true)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::KotlinTopLevelDeclarationCount)
    }
}
