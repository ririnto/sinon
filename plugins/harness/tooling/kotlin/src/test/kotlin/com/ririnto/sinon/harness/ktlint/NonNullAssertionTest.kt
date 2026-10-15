package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies that non-null assertions are reported without changing program behavior.
 */
class NonNullAssertionTest :
    FunSpec({
        test("keeps assertions on inherited nullable guards declared in another file") {
            val base = "open class Base { fun requireNotNull(value: String?): String? = value }\n"
            val source = "class Child : Base() { fun sample(value: String?): String = requireNotNull(value)!! }\n"
            assertSoftly {
                KtLintRuleTestEngine.execute(ruleProvider, base).formattedCode shouldBe base
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    diagnostics shouldContainExactlyInAnyOrder listOf(diagnostic(1, 82, canBeAutoCorrected = false))
                    formattedCode shouldBe source
                }
            }
        }

        test("keeps qualified nullable guard calls with a shadowed kotlin receiver") {
            val source = "fun sample(kotlin: Guards, value: String?): String = kotlin.requireNotNull(value)!!\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder listOf(diagnostic(1, 82, canBeAutoCorrected = false))
                formattedCode shouldBe source
            }
        }

        test("keeps unresolved non null assertion on require not null") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun sample(value: String?): String = requireNotNull(value)!!\n")) {
                diagnostics shouldContainExactlyInAnyOrder listOf(diagnostic(1, 59, canBeAutoCorrected = false))
                formattedCode shouldBe "fun sample(value: String?): String = requireNotNull(value)!!\n"
            }
        }

        test("keeps unresolved non null assertion on check not null") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun sample(value: String?): String = checkNotNull(value)!!\n")) {
                diagnostics shouldContainExactlyInAnyOrder listOf(diagnostic(1, 57, canBeAutoCorrected = false))
                formattedCode shouldBe "fun sample(value: String?): String = checkNotNull(value)!!\n"
            }
        }

        test("keeps assertion with an explicit stdlib guard import") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import kotlin.requireNotNull

                    fun sample(value: String?): String = requireNotNull(value)!!
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder listOf(diagnostic(3, 59, canBeAutoCorrected = false))
                formattedCode shouldBe
                    """
                    import kotlin.requireNotNull

                    fun sample(value: String?): String = requireNotNull(value)!!
                    """.trimIndent() + "\n"
            }
        }

        test("reports bare variable assertion without changing the exception type") {
            val source = "fun sample(value: String?): String = value!!\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder listOf(diagnostic(1, 43, canBeAutoCorrected = false))
                formattedCode shouldBe source
            }
        }

        test("reports member access on nullable receiver without rewriting") {
            val source = "fun sample(value: String?): Int = value!!.length\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder listOf(diagnostic(1, 40, canBeAutoCorrected = false))
                formattedCode shouldBe source
            }
        }

        test("reports index access without rewriting") {
            val source =
                """
                fun sample(map: Map<String, String>): String = map["key"]!!
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder listOf(diagnostic(1, 58, canBeAutoCorrected = false))
                formattedCode shouldBe source
            }
        }

        test("reports member function with a guard like name without rewriting") {
            val source =
                """
                class Service {
                    fun requireNonNull(): String = "value"
                }
                fun sample(service: Service): String = service.requireNonNull()!!
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder listOf(diagnostic(4, 64, canBeAutoCorrected = false))
                formattedCode shouldBe source
            }
        }

        test("keeps assertion on a user defined guard") {
            val source =
                """
                fun requireNotNull(value: Any?): String? = value?.toString()
                fun sample(value: String?): String = requireNotNull(value)!!
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder listOf(diagnostic(2, 59, canBeAutoCorrected = false))
                formattedCode shouldBe source
            }
        }

        test("keeps assertion when a star import could provide the guard") {
            val source =
                """
                import custom.guards.*

                fun sample(value: String?): String = requireNotNull(value)!!
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder listOf(diagnostic(3, 59, canBeAutoCorrected = false))
                formattedCode shouldBe source
            }
        }

        test("keeps assertion when a custom guard is imported") {
            val source =
                """
                import com.example.requireNotNull

                fun sample(value: Any?): Int = requireNotNull(value)!!
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder listOf(diagnostic(3, 53, canBeAutoCorrected = false))
                formattedCode shouldBe source
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::NonNullAssertion)

        private fun diagnostic(
            line: Int,
            column: Int,
            canBeAutoCorrected: Boolean
        ): KtLintRuleTestEngine.Diagnostic =
            KtLintRuleTestEngine.Diagnostic(
                line,
                column,
                "harness:non-null-assertion",
                canBeAutoCorrected
            )
    }
}
