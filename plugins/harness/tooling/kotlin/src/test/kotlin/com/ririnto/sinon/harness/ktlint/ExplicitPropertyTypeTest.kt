package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies the explicit property type rule.
 */
class ExplicitPropertyTypeTest :
    FunSpec({
        test("autocorrects literal member properties") {
            listOf(
                "class C { val count = 42 }" to "class C { val count: kotlin.Int = 42 }",
                "class C { val overflow = 3000000000 }" to "class C { val overflow: kotlin.Long = 3000000000 }",
                "class C { val id = 42L }" to "class C { val id: kotlin.Long = 42L }",
                "class C { val enabled = true }" to "class C { val enabled: kotlin.Boolean = true }",
                """class C { val name = "foo" }""" to """class C { val name: kotlin.String = "foo" }""",
                "class C { val sep = ',' }" to "class C { val sep: kotlin.Char = ',' }",
                "class C { val r = 3.14f }" to "class C { val r: kotlin.Float = 3.14f }",
                "class C { val pi = 3.14 }" to "class C { val pi: kotlin.Double = 3.14 }"
            ).forEach { (source, expected) ->
                val name = source.substringAfter("val ").substringBefore(" =")
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    assertSoftly(diagnostics) {
                        shouldContainExactlyInAnyOrder(
                            listOf(
                                KtLintRuleTestEngine.Diagnostic(
                                    1,
                                    source.indexOf(name) + 1,
                                    "harness:explicit-property-type",
                                    canBeAutoCorrected = true
                                )
                            )
                        )
                    }
                    formattedCode shouldBe expected
                }
            }
        }

        test("autocorrects companion object property") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "class C { companion object { val n = 42 } }")) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                34,
                                "harness:explicit-property-type",
                                canBeAutoCorrected = true
                            )
                        )
                    )
                }
                formattedCode shouldBe "class C { companion object { val n: kotlin.Int = 42 } }"
            }
        }

        test("autocorrects object property") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "object O { val n = 42 }")) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(1, 16, "harness:explicit-property-type", canBeAutoCorrected = true)
                        )
                    )
                }
                formattedCode shouldBe "object O { val n: kotlin.Int = 42 }"
            }
        }

        test("leaves unsafe initializers lint only") {
            listOf(
                "class C { val x = compute() }",
                "class C { val x = -1 }",
                "class C { val x = 1 + 2 }",
                "class C { val x by lazy { 1 } }",
                "class C { val x = null }",
                "class C { val x = 1u }",
                "class C { val x = 1UL }"
            ).forEach { source ->
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    assertSoftly(diagnostics) {
                        shouldContainExactlyInAnyOrder(
                            listOf(
                                KtLintRuleTestEngine.Diagnostic(
                                    1,
                                    15,
                                    "harness:explicit-property-type",
                                    canBeAutoCorrected = false
                                )
                            )
                        )
                    }
                }
            }
        }

        test("flags only untyped member properties") {
            listOf(
                "fun f() { val x = 42 }",
                "val top: Int = 42",
                "private val top: Int = 42",
                "class C { val x: Int = 42 }"
            ).forEach { source ->
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    assertSoftly(diagnostics) {
                        shouldContainExactlyInAnyOrder(emptyList())
                    }
                    formattedCode shouldBe source
                }
            }
        }

        test("autocorrects top level properties") {
            listOf(
                "val top = 42" to "val top: kotlin.Int = 42",
                "private val top = \"text\"" to "private val top: kotlin.String = \"text\"",
                "internal var top = true" to "internal var top: kotlin.Boolean = true"
            ).forEach { (source, expected) ->
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    assertSoftly(diagnostics) {
                        shouldContainExactlyInAnyOrder(
                            listOf(
                                KtLintRuleTestEngine.Diagnostic(
                                    1,
                                    source.indexOf("top") + 1,
                                    "harness:explicit-property-type",
                                    canBeAutoCorrected = true
                                )
                            )
                        )
                    }
                    formattedCode shouldBe expected
                }
            }
        }

        test("leaves unsafe top level initializers lint only") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "private val top = compute()")) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                13,
                                "harness:explicit-property-type",
                                canBeAutoCorrected = false
                            )
                        )
                    )
                }
            }
        }

        test("ignores script properties") {
            val source = "val top = 42\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source, isKotlinScript = true)) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
        }

        test("autocorrects property with annotation referencing name") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, """class C { @Ann(name = "name") val name = 42 }""")) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                35,
                                "harness:explicit-property-type",
                                canBeAutoCorrected = true
                            )
                        )
                    )
                }
                formattedCode shouldBe """class C { @Ann(name = "name") val name: kotlin.Int = 42 }"""
            }
        }

        test("uses built in types when declarations shadow their simple names") {
            val source =
                """
                class String
                class Int
                val message = "text"
                val count = 42
                """.trimIndent() + "\n"
            val expected =
                """
                class String
                class Int
                val message: kotlin.String = "text"
                val count: kotlin.Int = 42
                """.trimIndent() + "\n"
            val firstPass = KtLintRuleTestEngine.execute(ruleProvider, source)
            assertSoftly(firstPass) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                3,
                                source.indexOf("message") - source.lastIndexOf('\n', source.indexOf("message")),
                                "harness:explicit-property-type",
                                canBeAutoCorrected = true
                            ),
                            KtLintRuleTestEngine.Diagnostic(
                                4,
                                source.indexOf("count") - source.lastIndexOf('\n', source.indexOf("count")),
                                "harness:explicit-property-type",
                                canBeAutoCorrected = true
                            )
                        )
                    )
                }
            }
            val secondPass = KtLintRuleTestEngine.execute(ruleProvider, firstPass.formattedCode)
            assertSoftly(secondPass) {
                formattedCode shouldBe expected
            }
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, secondPass.formattedCode)) {
                formattedCode shouldBe expected
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::ExplicitPropertyType)
    }
}
