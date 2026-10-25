package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/**
 * Verifies the explicit function return type rule.
 */
class ExplicitFunctionReturnTypeTest :
    FunSpec({
        test("autocorrects bare literals") {
            listOf(
                """
                fun greeting() = "hi"
                """.trimIndent() to
                    """
                    fun greeting(): kotlin.String = "hi"
                    """.trimIndent(),
                "fun flag() = true" to "fun flag(): kotlin.Boolean = true",
                "fun first() = 'a'" to "fun first(): kotlin.Char = 'a'",
                "fun count() = 42" to "fun count(): kotlin.Int = 42",
                "fun overflow() = 3000000000" to "fun overflow(): kotlin.Long = 3000000000",
                "fun bigCount() = 42L" to "fun bigCount(): kotlin.Long = 42L",
                "fun ratio() = 3.14" to "fun ratio(): kotlin.Double = 3.14",
                "fun precise() = 3.14f" to "fun precise(): kotlin.Float = 3.14f"
            ).forEach { (sourceLine, expectedLine) ->
                val source = "$sourceLine\n"
                val name = sourceLine.substringAfter("fun ").substringBefore("(")
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                    assertSoftly(diagnostics) {
                        shouldContainExactlyInAnyOrder(
                            listOf(
                                KtLintRuleTestEngine.Diagnostic(
                                    1,
                                    source.indexOf(name) + 1,
                                    "harness:explicit-function-return-type",
                                    canBeAutoCorrected = true
                                )
                            )
                        )
                    }
                    formattedCode shouldBe "$expectedLine\n"
                }
            }
        }

        test("leaves unsafe shapes lint only") {
            assertSoftly {
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun compute() = calculate()\n")) {
                    assertSoftly(diagnostics) {
                        shouldContainExactlyInAnyOrder(
                            listOf(
                                KtLintRuleTestEngine.Diagnostic(
                                    1,
                                    5,
                                    "harness:explicit-function-return-type",
                                    canBeAutoCorrected = false
                                )
                            )
                        )
                    }
                }
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun neg() = -1\n")) {
                    assertSoftly(diagnostics) {
                        shouldContainExactlyInAnyOrder(
                            listOf(
                                KtLintRuleTestEngine.Diagnostic(
                                    1,
                                    5,
                                    "harness:explicit-function-return-type",
                                    canBeAutoCorrected = false
                                )
                            )
                        )
                    }
                }
                assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun composed() = if (b) 1 else 2\n")) {
                    assertSoftly(diagnostics) {
                        shouldContainExactlyInAnyOrder(
                            listOf(
                                KtLintRuleTestEngine.Diagnostic(
                                    1,
                                    5,
                                    "harness:explicit-function-return-type",
                                    canBeAutoCorrected = false
                                )
                            )
                        )
                    }
                }
            }
        }

        test("leaves override functions lint only") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    override fun name() = "x"
                    """.trimIndent() + "\n"
                )
            ) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                14,
                                "harness:explicit-function-return-type",
                                canBeAutoCorrected = false
                            )
                        )
                    )
                }
            }
        }

        test("ignores already typed and block functions") {
            val source =
                """
                fun already(): Int = 42
                fun block() { val x = 1 }
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
        }

        test("ignores explicit unit expression body") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun noop() = Unit\n")) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe "fun noop() = Unit\n"
            }
        }

        test("ignores explicit kotlin unit expression body") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun noop() = kotlin.Unit\n")) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe "fun noop() = kotlin.Unit\n"
            }
        }

        test("strips explicit unit return type from expression body") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun noop(): Unit = Unit\n")) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                13,
                                "harness:explicit-function-return-type",
                                canBeAutoCorrected = true
                            )
                        )
                    )
                }
                formattedCode shouldBe "fun noop() = Unit\n"
            }
        }

        test("strips explicit kotlin unit return type") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun noop(): kotlin.Unit {}\n")) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                13,
                                "harness:explicit-function-return-type",
                                canBeAutoCorrected = true
                            )
                        )
                    )
                }
                formattedCode shouldBe "fun noop() {}\n"
            }
        }

        test("ignores qualified non kotlin unit return type") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun noop(): other.kotlin.Unit {}\n")) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe "fun noop(): other.kotlin.Unit {}\n"
            }
        }

        test("accepts load bearing unit return type on expression bodied call") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun clear(): Unit = delegate.clear()\n")) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe "fun clear(): Unit = delegate.clear()\n"
            }
        }

        test("accepts load bearing kotlin unit return type on expression bodied call") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun clear(): kotlin.Unit = delegate.clear()\n")) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe "fun clear(): kotlin.Unit = delegate.clear()\n"
            }
        }

        test("still requires explicit type on untyped expression bodied unit call") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun clear() = delegate.clear()\n")) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                5,
                                "harness:explicit-function-return-type",
                                canBeAutoCorrected = false
                            )
                        )
                    )
                }
            }
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                13,
                                "harness:explicit-function-return-type",
                                canBeAutoCorrected = true
                            )
                        )
                    )
                }
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                5,
                                "harness:explicit-function-return-type",
                                canBeAutoCorrected = true
                            )
                        )
                    )
                }
                formattedCode shouldBe
                    """
                    fun greet(prefix: String = "hi"): kotlin.String = "hello"
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
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                2,
                                5,
                                "harness:explicit-function-return-type",
                                canBeAutoCorrected = true
                            )
                        )
                    )
                }
                formattedCode shouldBe
                    """
                    @Ann(enabled = true)
                    fun greet(): kotlin.String = "hello"
                    """.trimIndent() + "\n"
            }
        }

        test("strips unit return type preserving whitespace before colon") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun noop() : Unit {}\n")) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                14,
                                "harness:explicit-function-return-type",
                                canBeAutoCorrected = true
                            )
                        )
                    )
                }
                formattedCode shouldBe "fun noop() {}\n"
            }
        }

        test("uses built in return types when declarations shadow their simple names") {
            val source =
                """
                class String
                class Int
                fun text() = "text"
                fun count() = 42
                """.trimIndent() + "\n"
            val expected =
                """
                class String
                class Int
                fun text(): kotlin.String = "text"
                fun count(): kotlin.Int = 42
                """.trimIndent() + "\n"
            val firstPass = KtLintRuleTestEngine.execute(ruleProvider, source)
            assertSoftly(firstPass) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                3,
                                source.indexOf("text") - source.lastIndexOf('\n', source.indexOf("text")),
                                "harness:explicit-function-return-type",
                                canBeAutoCorrected = true
                            ),
                            KtLintRuleTestEngine.Diagnostic(
                                4,
                                source.indexOf("count") - source.lastIndexOf('\n', source.indexOf("count")),
                                "harness:explicit-function-return-type",
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

        test("keeps a generic Unit type parameter as the return contract") {
            val source =
                """
                fun interface Supplier<Unit> { fun get(): Unit }
                fun <Unit> identity(value: Unit): Unit = value
                """.trimIndent() + "\n"
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, source)) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(emptyList())
                }
                formattedCode shouldBe source
            }
        }

        test("removes genuine Unit annotations without changing side effect bodies") {
            val source =
                """
                fun noop(): kotlin.Unit {}
                class Counter(var value: Int = 0)
                fun increment(counter: Counter): Unit { counter.value++ }
                """.trimIndent() + "\n"
            val expected =
                """
                fun noop() {}
                class Counter(var value: Int = 0)
                fun increment(counter: Counter) { counter.value++ }
                """.trimIndent() + "\n"
            val result = KtLintRuleTestEngine.execute(ruleProvider, source)
            assertSoftly(result) {
                assertSoftly(diagnostics) {
                    shouldContainExactlyInAnyOrder(
                        listOf(
                            KtLintRuleTestEngine.Diagnostic(
                                1,
                                source.indexOf("kotlin.Unit") + 1,
                                "harness:explicit-function-return-type",
                                canBeAutoCorrected = true
                            ),
                            KtLintRuleTestEngine.Diagnostic(
                                3,
                                source.indexOf(": Unit") + 2 - source.lastIndexOf('\n', source.indexOf(": Unit")),
                                "harness:explicit-function-return-type",
                                canBeAutoCorrected = true
                            )
                        )
                    )
                }
                formattedCode shouldBe expected
            }
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, result.formattedCode)) {
                formattedCode shouldBe expected
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::ExplicitFunctionReturnType)
    }
}
