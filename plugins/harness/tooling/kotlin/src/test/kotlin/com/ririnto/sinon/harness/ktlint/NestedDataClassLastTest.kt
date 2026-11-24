package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies the nested data class last rule.
 */
class NestedDataClassLastTest :
    FunSpec({
        test("reports misplaced data classes without changing members or detached comments") {
            val source =
                """
                class Inventory {
                    data class Row(val id: Int)
                    data class Key(val id: Int)
                    fun row(): Row = Row(0)

                    // Preserve this tail comment.
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics.shouldContainExactlyInAnyOrder(
                    KtLintRuleTestEngine.Diagnostic(2, 16, "harness:nested-data-class-last", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(3, 16, "harness:nested-data-class-last", canBeAutoCorrected = false)
                )
                formattedCode shouldBe source
            }
        }

        test("reports misplaced data classes without rewriting multiline raw strings") {
            val source =
                """
                class Direct {
                    data class Item(val text: String = ${"\"\"\""}
                        first
                          second
                    ${"\"\"\""})
                    fun load(): Item = Item()
                }

                class Indirect {
                    data class Row(val id: Int)
                    val text = listOf(${"\"\"\""}
                        first
                          second
                    ${"\"\"\""}.trim())
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics.shouldContainExactlyInAnyOrder(
                    KtLintRuleTestEngine.Diagnostic(2, 16, "harness:nested-data-class-last", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(10, 16, "harness:nested-data-class-last", canBeAutoCorrected = false)
                )
                formattedCode shouldBe source
            }
        }

        test("accepts trailing data classes and leaves enum bodies alone") {
            val source =
                """
                enum class Flag {
                    ON;
                    data class Row(val id: Int)
                    fun row(): Row = Row(0)
                }

                class Container {
                    companion object {
                        fun key(): Key = Key(0)
                        data class Key(val id: Int)
                    }
                    data class Row(val id: Int)
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldBe emptyList()
                formattedCode shouldBe source
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::NestedDataClassLast)
    }
}
