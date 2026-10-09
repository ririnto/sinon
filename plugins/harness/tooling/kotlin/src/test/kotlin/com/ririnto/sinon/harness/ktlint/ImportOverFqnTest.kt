package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ImportOverFqnTest :
    FunSpec({
        test("fully qualified type is rewritten and imported") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "val value: kotlin.collections.ArrayList<String> = kotlin.collections.ArrayList<String>()\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            12,
                            "fully qualified name `kotlin.collections.ArrayList` used inline; add an import and use the simple name",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    import kotlin.collections.ArrayList

                    val value: ArrayList<String> = kotlin.collections.ArrayList<String>()
                    """.trimIndent() + "\n"
            }
        }

        test("multiple type packages add multiple imports") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    val a: java.util.ArrayList<String>? = null
                    val b: kotlin.collections.LinkedList<String>? = null
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            8,
                            "fully qualified name `java.util.ArrayList` used inline; add an import and use the simple name"
                        ),
                        KtLintRuleTestEngine.Diagnostic(
                            2,
                            8,
                            "fully qualified name `kotlin.collections.LinkedList` used inline; add an import and use the simple name"
                        )
                    )
                formattedCode shouldBe
                    """
                    import java.util.ArrayList
                    import kotlin.collections.LinkedList

                    val a: ArrayList<String>? = null
                    val b: LinkedList<String>? = null
                    """.trimIndent() + "\n"
            }
        }

        test("new type import is inserted alphabetically with existing imports") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import gamma.delta.Baz

                    val value: alpha.beta.Foo? = null
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            3,
                            12,
                            "fully qualified name `alpha.beta.Foo` used inline; add an import and use the simple name",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    import alpha.beta.Foo
                    import gamma.delta.Baz

                    val value: Foo? = null
                    """.trimIndent() + "\n"
            }
        }

        test("a declaration with the candidate name is left unchanged") {
            val source =
                """
                typealias ArrayList = Any

                val value: java.util.ArrayList<String>? = null
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("same name import collision is left unchanged") {
            val source =
                """
                import other.ArrayList

                val value: java.util.ArrayList<String>? = null
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("same path import allows shortening a type") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import kotlin.collections.ArrayList

                    val value: kotlin.collections.ArrayList<String>? = null
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            3,
                            12,
                            "fully qualified name `kotlin.collections.ArrayList` used inline; add an import and use the simple name",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    import kotlin.collections.ArrayList

                    val value: ArrayList<String>? = null
                    """.trimIndent() + "\n"
            }
        }

        test("same path alias import is left unchanged") {
            val source =
                """
                import kotlin.collections.ArrayList as JList

                val value: kotlin.collections.ArrayList<String>? = null
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("alias collision is left unchanged") {
            val source =
                """
                import other.Widget as ArrayList

                val value: java.util.ArrayList<String>? = null
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("distinct fully qualified types with one simple name are left unchanged") {
            val source =
                """
                val first: alpha.one.Widget? = null
                val second: beta.two.Widget? = null
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("a type from the current package is left unchanged") {
            val source =
                """
                package java.util

                val value: java.util.ArrayList<String>? = null
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("wildcard imports suppress import preference findings") {
            val source =
                """
                import other.*

                val value: java.util.ArrayList<String>? = null
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("existing implicit type use suppresses a conflicting import preference") {
            val source =
                """
                val kotlinList: List<String>? = null
                val javaList: java.util.List<List<String>>? = null
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("expression chains are left unchanged without compiler symbol resolution") {
            val source =
                """
                object WidgetRoot {
                    const val WIDGET: Int = 1
                }

                class FooHolder {
                    val foo: WidgetRoot = WidgetRoot
                }

                fun test() {
                    val com: FooHolder = FooHolder()
                    val value = com.foo.WIDGET
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("shadowed receiver fixture keeps its value semantics") {
            ImportOverFqnShadowedReceiverFixture().receiverChain() shouldBe 1
        }

        test("already short type name is a no op") {
            val source = "val value: ArrayList<String> = ArrayList()\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::ImportOverFqn)
    }
}
