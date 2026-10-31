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
 * Verifies KDoc requirements on public types and functions.
 */
class PublicDeclarationDocCommentTest :
    FunSpec({
        test("ignores local class members and constructor properties") {
            val source =
                """
                /**
                 * Performs local work.
                 */
                fun sample() {
                    class Local(val value: String) {
                        val label: String = value
                        fun run() {}
                    }
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
        }

        test("ignores members of locally held anonymous objects") {
            val source =
                """
                /**
                 * Performs local work.
                 */
                fun sample() {
                    val local = object {
                        val value: String = "value"
                        fun run() {}
                    }
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
        }

        test("is enabled by default for effective public declarations") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    class Service {
                        fun work() {
                        }
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(1, 7, "harness:public-declaration-doc-comment", false),
                            KtLintRuleTestEngine.Diagnostic(2, 9, "harness:public-declaration-doc-comment", false)
                        )
                    )
                }
            }
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
        }

        test("requires documentation on public interfaces") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    interface Repository {
                        fun find(): String
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(1, 11, "harness:public-declaration-doc-comment", false),
                            KtLintRuleTestEngine.Diagnostic(2, 9, "harness:public-declaration-doc-comment", false)
                        )
                    )
                }
            }
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
        }

        test("does not require boilerplate on properties") {
            val source =
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
                    ) {
                        val display: String = label
                    }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::PublicDeclarationDocComment)
    }
}
