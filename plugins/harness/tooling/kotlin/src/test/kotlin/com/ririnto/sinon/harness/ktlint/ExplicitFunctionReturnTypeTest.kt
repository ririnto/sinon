package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ExplicitFunctionReturnTypeTest :
    FunSpec({
        test("autocorrects bare literals") {
            listOf(
                """
                fun greeting() = "hi"
                """.trimIndent() to
                    """
                    fun greeting(): String = "hi"
                    """.trimIndent(),
                "fun flag() = true" to "fun flag(): Boolean = true",
                "fun first() = 'a'" to "fun first(): Char = 'a'",
                "fun count() = 42" to "fun count(): Int = 42",
                "fun overflow() = 3000000000" to "fun overflow(): Long = 3000000000",
                "fun bigCount() = 42L" to "fun bigCount(): Long = 42L",
                "fun ratio() = 3.14" to "fun ratio(): Double = 3.14",
                "fun precise() = 3.14f" to "fun precise(): Float = 3.14f"
            ).forEach { (sourceLine, expectedLine) ->
                val source = "$sourceLine\n"
                val name = sourceLine.substringAfter("fun ").substringBefore("(")
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    diagnostics shouldContainExactlyInAnyOrder
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                source.indexOf(name) + 1,
                                "declare an explicit return type on named function `$name`",
                                canBeAutoCorrected = true
                            )
                        )
                    formattedCode shouldBe "$expectedLine\n"
                }
            }
        }

        test("leaves unsafe shapes lint only") {
            assertSoftly {
                KtLintRuleTestEngine.execute(ruleProvider, "fun compute() = calculate()\n").diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            5,
                            "declare an explicit return type on named function `compute`",
                            canBeAutoCorrected = false
                        )
                    )
                KtLintRuleTestEngine.execute(ruleProvider, "fun neg() = -1\n").diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            5,
                            "declare an explicit return type on named function `neg`",
                            canBeAutoCorrected = false
                        )
                    )
                KtLintRuleTestEngine.execute(ruleProvider, "fun composed() = if (b) 1 else 2\n").diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            5,
                            "declare an explicit return type on named function `composed`",
                            canBeAutoCorrected = false
                        )
                    )
            }
        }

        test("leaves override functions lint only") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    override fun name() = "x"
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        14,
                        "declare an explicit return type on named function `name`",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("ignores already typed and block functions") {
            val source =
                """
                fun already(): Int = 42
                fun block() { val x = 1 }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe source
            }
        }

        test("ignores explicit unit expression body") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun noop() = Unit\n")) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe "fun noop() = Unit\n"
            }
        }

        test("ignores explicit kotlin unit expression body") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun noop() = kotlin.Unit\n")) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe "fun noop() = kotlin.Unit\n"
            }
        }

        test("strips explicit unit return type from expression body") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun noop(): Unit = Unit\n")) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            13,
                            "omit the redundant `Unit` return type on named function `noop`",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe "fun noop() = Unit\n"
            }
        }

        test("strips explicit kotlin unit return type") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun noop(): kotlin.Unit {}\n")) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            13,
                            "omit the redundant `Unit` return type on named function `noop`",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe "fun noop() {}\n"
            }
        }

        test("ignores qualified non kotlin unit return type") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun noop(): other.kotlin.Unit {}\n")) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe "fun noop(): other.kotlin.Unit {}\n"
            }
        }

        test("accepts load bearing unit return type on expression bodied call") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun clear(): Unit = delegate.clear()\n")) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe "fun clear(): Unit = delegate.clear()\n"
            }
        }

        test("accepts load bearing kotlin unit return type on expression bodied call") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun clear(): kotlin.Unit = delegate.clear()\n")) {
                diagnostics shouldContainExactlyInAnyOrder emptyList()
                formattedCode shouldBe "fun clear(): kotlin.Unit = delegate.clear()\n"
            }
        }

        test("still requires explicit type on untyped expression bodied unit call") {
            KtLintRuleTestEngine.execute(ruleProvider, "fun clear() = delegate.clear()\n").diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        5,
                        "declare an explicit return type on named function `clear`",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("strips explicit unit return type from block body") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun noop(): Unit { println("hi") }
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            13,
                            "omit the redundant `Unit` return type on named function `noop`",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    fun noop() { println("hi") }
                    """.trimIndent() + "\n"
            }
        }

        test("autocorrects function with default parameter") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun greet(prefix: String = "hi") = "hello"
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            5,
                            "declare an explicit return type on named function `greet`",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    fun greet(prefix: String = "hi"): String = "hello"
                    """.trimIndent() + "\n"
            }
        }

        test("autocorrects function with annotation argument") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    @Ann(enabled = true)
                    fun greet() = "hello"
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            2,
                            5,
                            "declare an explicit return type on named function `greet`",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    @Ann(enabled = true)
                    fun greet(): String = "hello"
                    """.trimIndent() + "\n"
            }
        }

        test("strips unit return type preserving whitespace before colon") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun noop() : Unit {}\n")) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            14,
                            "omit the redundant `Unit` return type on named function `noop`",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe "fun noop() {}\n"
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::ExplicitFunctionReturnType)
    }
}
