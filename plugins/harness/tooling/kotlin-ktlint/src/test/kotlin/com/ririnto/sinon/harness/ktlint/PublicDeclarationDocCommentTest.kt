package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class PublicDeclarationDocCommentTest :
    FunSpec({
        test("requires documentation on effective public declarations") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    class Service(
                        val dependency: String
                    )
                    """.trimIndent() + "\n",
                    editorConfigOverride = EditorConfigOverride.from(PublicDeclarationDocComment.DOC_COMMENT_MODE to "on")
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(KtLintRuleTestEngine.Diagnostic(1, 7, "add a documentation comment to public declaration `Service`", false))
        }

        test("requires documentation on public interfaces") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    interface Repository {
                        fun find(): String
                    }
                    """.trimIndent() + "\n",
                    editorConfigOverride = EditorConfigOverride.from(PublicDeclarationDocComment.DOC_COMMENT_MODE to "on")
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(1, 11, "add a documentation comment to public declaration `Repository`", false),
                    KtLintRuleTestEngine.Diagnostic(2, 9, "add a documentation comment to public declaration `find`", false)
                )
        }

        test("accepts documentation and inherited override contracts") {
            val source =
                """
                /**
                 * Service contract.
                 */
                open class Service {
                    /**
                     * Performs work.
                     */
                    open fun work() {
                    }
                }

                class Child : Service() {
                    override fun work() {
                    }
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("ignores declarations hidden by internal or private enclosures") {
            val source =
                """
                internal class Hidden {
                    val value: String = "value"
                }

                private object Secret {
                    fun work() {
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
        private val ruleProvider: RuleProvider = RuleProvider(::PublicDeclarationDocComment)
    }
}
