package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class NonNullAssertionTest :
    FunSpec({
        test("autocorrects redundant non null assertion on require not null") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun sample(value: String?): String = requireNotNull(value)!!\n")) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            59,
                            "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe "fun sample(value: String?): String = requireNotNull(value)\n"
            }
        }

        test("autocorrects redundant non null assertion on check not null") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun sample(value: String?): String = checkNotNull(value)!!\n")) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            57,
                            "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe "fun sample(value: String?): String = checkNotNull(value)\n"
            }
        }

        test("autocorrects bare variable assertion to require not null") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun sample(value: String?): String = value!!\n")) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            43,
                            "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe "fun sample(value: String?): String = requireNotNull(value)\n"
            }
        }

        test("autocorrects member access on nullable receiver") {
            assertSoftly(KtLintRuleTestEngine.execute(ruleProvider, "fun sample(value: String?): Int = value!!.length\n")) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            40,
                            "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe "fun sample(value: String?): Int = requireNotNull(value).length\n"
            }
        }

        test("autocorrects index access") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun sample(map: Map<String, String>): String = map["key"]!!
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            58,
                            "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    fun sample(map: Map<String, String>): String = requireNotNull(map["key"])
                    """.trimIndent() + "\n"
            }
        }

        test("autocorrects member function with similar name") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    class Service {
                        fun requireNonNull(): String = "value"
                    }
                    fun sample(service: Service): String = service.requireNonNull()!!
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            4,
                            64,
                            "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    class Service {
                        fun requireNonNull(): String = "value"
                    }
                    fun sample(service: Service): String = requireNotNull(service.requireNonNull())
                    """.trimIndent() + "\n"
            }
        }

        test("disables autocorrect when user defined guard shadows stdlib") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    fun requireNotNull(value: Any?): String = value.toString()
                    fun sample(value: String?): String = value!!
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        43,
                        "avoid non-null assertion `!!`; a user-defined `requireNotNull` shadows the standard library guard, so rewrite it manually with `requireNotNull(...)` or rename the shadow",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("disables autocorrect when star import could provide guard") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    import custom.guards.*

                    fun sample(value: String?): String = value!!
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        43,
                        "avoid non-null assertion `!!`; a user-defined `requireNotNull` shadows the standard library guard, so rewrite it manually with `requireNotNull(...)` or rename the shadow",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("keeps autocorrect when only stdlib guard is imported explicitly") {
            assertSoftly(
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import kotlin.requireNotNull

                    fun sample(value: String?): String = value!!
                    """.trimIndent() + "\n"
                )
            ) {
                diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            3,
                            43,
                            "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard",
                            canBeAutoCorrected = true
                        )
                    )
                formattedCode shouldBe
                    """
                    import kotlin.requireNotNull

                    fun sample(value: String?): String = requireNotNull(value)
                    """.trimIndent() + "\n"
            }
        }

        test("unaliased custom guard import disables autocorrect") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    """
                    import com.example.requireNotNull

                    fun sample(value: Any?): Int = value!!
                    """.trimIndent() + "\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        37,
                        "avoid non-null assertion `!!`; a user-defined `requireNotNull` shadows the standard library guard, so rewrite it manually with `requireNotNull(...)` or rename the shadow",
                        canBeAutoCorrected = false
                    )
                )
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::NonNullAssertion)
    }
}
