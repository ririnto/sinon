package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.RuleExecution
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.createRuleExecutionEditorConfigProperty
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies that public declarations require KDoc without extra configuration.
 */
class PublicDeclarationDocCommentTest :
    FunSpec({
        test("is enabled by default for effective public declarations") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    class Service {
                        fun work() {
                        }
                    }
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(1, 7, "add a documentation comment to public declaration `Service`", false),
                    KtLintRuleTestEngine.Diagnostic(2, 9, "add a documentation comment to public declaration `work`", false)
                )
        }

        test("can be opted out through the native rule execution property") {
            val source = "class Service\n"
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source,
                    editorConfigOverride =
                        EditorConfigOverride.from(
                            ruleProvider.ruleId.createRuleExecutionEditorConfigProperty() to RuleExecution.disabled
                        )
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("requires documentation on public interfaces") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    interface Repository {
                        fun find(): String
                    }
                    """.trimIndent() + "\n"
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

                    /**
                     * Exposes a name.
                     */
                    open val name: String = "service"
                }

                /**
                 * Child contract.
                 */
                class Child : Service() {
                    override fun work() {
                    }

                    override val name: String = "child"
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("skips override properties and override constructor properties") {
            val source =
                """
                /**
                 * Child contract.
                 *
                 * @property name Exposed name.
                 */
                class Child(
                    override val name: String
                ) : Named {
                    override var label: String = name
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("requires documentation on public primary constructor properties") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    /**
                     * Identity.
                     */
                    data class Identity(
                        val id: Int,
                        var label: String,
                        protected val owner: String,
                        private val secret: String,
                        plain: String
                    )
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(5, 9, "add a documentation comment to public declaration `id`", false),
                    KtLintRuleTestEngine.Diagnostic(6, 9, "add a documentation comment to public declaration `label`", false),
                    KtLintRuleTestEngine.Diagnostic(7, 19, "add a documentation comment to public declaration `owner`", false)
                )
        }

        test("accepts property tags on the class and documentation on the parameter") {
            val source =
                """
                /**
                 * Identity.
                 *
                 * @property id Stable identifier.
                 */
                class Identity(
                    val id: Int,
                    /**
                     * Display label.
                     */
                    val label: String
                )
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("treats a private constructor as public for its properties") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    /**
                     * Identity.
                     */
                    class Identity private constructor(
                        val id: Int
                    )
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(KtLintRuleTestEngine.Diagnostic(5, 9, "add a documentation comment to public declaration `id`", false))
        }

        test("ignores declarations hidden by internal or private enclosures") {
            val source =
                """
                internal class Hidden(
                    val id: Int
                ) {
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
