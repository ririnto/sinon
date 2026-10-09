package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ImportOverFqnTest :
    FunSpec({
        test("simple fqn is rewritten and imported") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "val value = kotlin.collections.ArrayList<String>()\n")) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            13,
                            "fully qualified name `kotlin.collections.ArrayList` used inline; add an import and use the simple name",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    import kotlin.collections.ArrayList

                    val value = ArrayList<String>()
                    """.trimIndent() + "\n"
            }
        }

        test("multiple packages add multiple imports") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    val a = java.util.ArrayList<String>()
                    val b = kotlin.collections.LinkedList<String>()
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            9,
                            "fully qualified name `java.util.ArrayList` used inline; add an import and use the simple name"
                        ),
                        KtLintRuleTestEngine.Diagnostic(
                            2,
                            9,
                            "fully qualified name `kotlin.collections.LinkedList` used inline; add an import and use the simple name"
                        )
                    )
                formattedCode shouldBe
                    """
                    import java.util.ArrayList
                    import kotlin.collections.LinkedList

                    val a = ArrayList<String>()
                    val b = LinkedList<String>()
                    """.trimIndent() + "\n"
            }
        }

        test("inserts new import in alphabetical order with existing imports") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import gamma.delta.Baz

                    val value = alpha.beta.Foo()
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            3,
                            13,
                            "fully qualified name `alpha.beta.Foo` used inline; add an import and use the simple name",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    import alpha.beta.Foo
                    import gamma.delta.Baz

                    val value = Foo()
                    """.trimIndent() + "\n"
            }
        }

        test("local name collision is left unchanged") {
            val source = "fun test() { val ArrayList = 1; println(java.util.ArrayList<String>()) }\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("same name import collision is left unchanged") {
            val source =
                """
                import other.ArrayList
                val value = java.util.ArrayList<String>()
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("same path import allows shortening") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import kotlin.collections.ArrayList

                    val value = kotlin.collections.ArrayList<String>()
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            3,
                            13,
                            "fully qualified name `kotlin.collections.ArrayList` used inline; add an import and use the simple name",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    import kotlin.collections.ArrayList

                    val value = ArrayList<String>()
                    """.trimIndent() + "\n"
            }
        }

        test("same path alias import is left unchanged") {
            val source =
                """
                import kotlin.collections.ArrayList as JList

                val value = kotlin.collections.ArrayList<String>()
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

                val value = java.util.ArrayList<String>()
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("distinct fqns with the same simple name are left unchanged") {
            val source =
                """
                val first = alpha.one.Widget()
                val second = beta.two.Widget()
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

                val value = java.util.ArrayList<String>()
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
                val value = java.util.ArrayList<String>()
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("existing implicit type use suppresses a conflicting import preference") {
            val source =
                """
                val kotlinList: List<String> = emptyList()
                val javaList: java.util.List<List<String>> = emptyList()
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("existing implicit constructor use suppresses a conflicting import preference") {
            val source =
                """
                fun test() {
                    val javaThread = com.example.Thread()
                    val kotlinThread = Thread()
                    val threadName = Thread().name
                }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("already short name is no op") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "val value = ArrayList<String>()\n")) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe "val value = ArrayList<String>()\n"
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::ImportOverFqn)
    }
}
