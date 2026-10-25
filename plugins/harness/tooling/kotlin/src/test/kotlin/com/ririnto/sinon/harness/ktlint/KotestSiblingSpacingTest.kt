package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies that only proven Kotest sibling registrations receive exactly one separating blank line.
 */
class KotestSiblingSpacingTest :
    FunSpec({
        test("separates StringSpec tests and hooks with one blank line") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import io.kotest.core.spec.style.StringSpec

                    class StringSpecFixture : StringSpec({
                        beforeSpec { setup() }
                        "configured test".config(enabled = true) {}

                        "plain test" {}
                    })
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(listOf(spacing(4, 27)))
                }
                formattedCode shouldBe
                    """
                    import io.kotest.core.spec.style.StringSpec

                    class StringSpecFixture : StringSpec({
                        beforeSpec { setup() }

                        "configured test".config(enabled = true) {}

                        "plain test" {}
                    })
                    """.trimIndent() + "\n"
            }
        }

        test("separates FunSpec init and nested context registrations through an import alias") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import io.kotest.core.spec.style.FunSpec as ImportedFunSpec

                    class FunSpecInitFixture : ImportedFunSpec() {
                        init {
                            beforeSpec { setup() }
                            context("outer") {
                                test("first") { runWork() }
                                afterEach { cleanup() }

                                context("inner") {
                                    test("nested first") { runWork() }

                                    test("nested second") { runWork() }
                                }
                            }
                        }
                    }
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(listOf(spacing(5, 31), spacing(7, 40)))
                }
                formattedCode shouldBe
                    """
                    import io.kotest.core.spec.style.FunSpec as ImportedFunSpec

                    class FunSpecInitFixture : ImportedFunSpec() {
                        init {
                            beforeSpec { setup() }

                            context("outer") {
                                test("first") { runWork() }

                                afterEach { cleanup() }

                                context("inner") {
                                    test("nested first") { runWork() }

                                    test("nested second") { runWork() }
                                }
                            }
                        }
                    }
                    """.trimIndent() + "\n"
            }
        }

        test("collapses extra blank lines between siblings") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import io.kotest.core.spec.style.FunSpec

                    class ExtraFixture : FunSpec({
                        test("first") {}


                        test("second") {}
                    })
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(listOf(spacing(4, 21)))
                }
                formattedCode shouldBe
                    """
                    import io.kotest.core.spec.style.FunSpec

                    class ExtraFixture : FunSpec({
                        test("first") {}

                        test("second") {}
                    })
                    """.trimIndent() + "\n"
            }
        }

        test("keeps a blank line between a local declaration and the following test") {
            val source =
                """
                import io.kotest.core.spec.style.FunSpec

                class LocalFixture : FunSpec({
                    val shared: Int = 1

                    test("first") {}
                    val other: Int = 2
                    test("second") {}
                })
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
        }

        test("leaves unproven registrations unchanged") {
            listOf(
                """
                class Business {
                    fun test(name: String, body: () -> Unit) {}

                    fun verify() {
                        test("first") {}
                        test("second") {}
                    }
                }
                """.trimIndent() + "\n",
                """
                import io.kotest.core.spec.style.FunSpec

                class ShadowedFixture : FunSpec({
                    fun test(name: String, body: () -> Unit) {}
                    test("business first") {}
                    test("business second") {}
                })
                """.trimIndent() + "\n",
                """
                import io.kotest.core.spec.style.FunSpec

                class GenericFixture<FunSpec> : FunSpec({
                    test("first") {}
                    test("second") {}
                })
                """.trimIndent() + "\n",
                """
                import io.kotest.core.spec.style.FunSpec

                fun build() {
                    class FunSpec(body: () -> Unit)
                    class LocalFixture : FunSpec({
                        test("first") {}
                        test("second") {}
                    })
                }
                """.trimIndent() + "\n",
                """
                import io.kotest.core.spec.style.FunSpec

                class ParameterFixture(test: (String, () -> Unit) -> Unit) : FunSpec({
                    test("first") {}
                    test("second") {}
                })
                """.trimIndent() + "\n",
                """
                import io.kotest.core.spec.style.FunSpec

                class DestructuredFixture : FunSpec({
                    val (test, other) = registrations()
                    test("first") {}
                    test("second") {}
                })
                """.trimIndent() + "\n",
                """
                import io.kotest.core.spec.style.StringSpec

                class ShadowedInvokeFixture : StringSpec({
                    operator fun String.invoke(block: () -> Unit) {}
                    "business first" {}
                    "business second" {}
                })
                """.trimIndent() + "\n",
                """
                import io.kotest.core.spec.style.FunSpec

                class LeafFixture : FunSpec({
                    test("leaf") {
                        execute()
                        verify()
                    }
                    // Keep this explanation attached to the following registration.
                    test("next") {}
                })
                """.trimIndent() + "\n",
                """
                import io.kotest.core.spec.style.FunSpec

                class SameLineFixture : FunSpec({
                    test("first") {}; test("second") {}
                })
                """.trimIndent() + "\n",
                """
                import io.kotest.core.spec.style.*

                class WildcardFixture : FunSpec({
                    test("first") {}
                    test("second") {}
                })
                """.trimIndent() + "\n",
                """
                import org.example.FunSpec

                class OtherFrameworkFixture : FunSpec({
                    test("first") {}
                    test("second") {}
                })
                """.trimIndent() + "\n"
            ).forEach { source ->
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    assertSoftly(diagnostics) {
                        shouldContainExactlyInAnyOrder(emptyList())
                    }
                    formattedCode shouldBe source
                }
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::KotestSiblingSpacing)

        private fun spacing(
            line: Int,
            column: Int
        ): KtLintRuleTestEngine.Diagnostic =
            KtLintRuleTestEngine.Diagnostic(
                line,
                column,
                "harness:kotest-sibling-spacing",
                canBeAutoCorrected = true
            )
    }
}
