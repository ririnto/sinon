package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class NonNullAssertionTest :
    FunSpec({
        test("autocorrects redundant non null assertion on require not null") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun sample(value: String?): String = requireNotNull(value)!!\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        59,
                        "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "fun sample(value: String?): String = requireNotNull(value)\n"
        }

        test("autocorrects redundant non null assertion on check not null") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun sample(value: String?): String = checkNotNull(value)!!\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        57,
                        "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "fun sample(value: String?): String = checkNotNull(value)\n"
        }

        test("autocorrects bare variable assertion to require not null") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun sample(value: String?): String = value!!\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        43,
                        "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "fun sample(value: String?): String = requireNotNull(value)\n"
        }

        test("autocorrects member access on nullable receiver") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun sample(value: String?): Int = value!!.length\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        40,
                        "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "fun sample(value: String?): Int = requireNotNull(value).length\n"
        }

        test("autocorrects index access") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun sample(map: Map<String, String>): String = map[\"key\"]!!\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        58,
                        "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "fun sample(map: Map<String, String>): String = requireNotNull(map[\"key\"])\n"
        }

        test("autocorrects member function with similar name") {
            val source =
                """
                class Service {
                    fun requireNonNull(): String = "value"
                }
                fun sample(service: Service): String = service.requireNonNull()!!
                """.trimIndent() + "\n"
            val expected =
                """
                class Service {
                    fun requireNonNull(): String = "value"
                }
                fun sample(service: Service): String = requireNotNull(service.requireNonNull())
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        64,
                        "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("disables autocorrect when user defined guard shadows stdlib") {
            val source =
                """
                fun requireNotNull(value: Any?): String = value.toString()
                fun sample(value: String?): String = value!!
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
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
            val source =
                """
                import custom.guards.*

                fun sample(value: String?): String = value!!
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
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
            val source = "import kotlin.requireNotNull\n\nfun sample(value: String?): String = value!!\n"
            val expected = "import kotlin.requireNotNull\n\nfun sample(value: String?): String = requireNotNull(value)\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        43,
                        "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe expected
        }

        test("unaliased custom guard import disables autocorrect") {
            val source =
                """
                import com.example.requireNotNull

                fun sample(value: Any?): Int = value!!
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
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
