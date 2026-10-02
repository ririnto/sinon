package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ExplicitPropertyTypeTest :
    FunSpec({
        test("autocorrects literal member properties") {
            val cases =
                listOf(
                    "class C { val count = 42 }" to "class C { val count: Int = 42 }",
                    "class C { val overflow = 3000000000 }" to "class C { val overflow: Long = 3000000000 }",
                    "class C { val id = 42L }" to "class C { val id: Long = 42L }",
                    "class C { val enabled = true }" to "class C { val enabled: Boolean = true }",
                    "class C { val name = \"foo\" }" to "class C { val name: String = \"foo\" }",
                    "class C { val sep = ',' }" to "class C { val sep: Char = ',' }",
                    "class C { val r = 3.14f }" to "class C { val r: Float = 3.14f }",
                    "class C { val pi = 3.14 }" to "class C { val pi: Double = 3.14 }"
                )
            cases.forEach { (source, expected) ->
                val name = source.substringAfter("val ").substringBefore(" =")
                val offset = source.indexOf(name) + 1
                val lintResult1 =
                    KtLintRuleTestEngine.execute(
                        ruleProvider,
                        source
                    )
                lintResult1.diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            offset,
                            "declare an explicit type on member property `$name`",
                            canBeAutoCorrected = true
                        )
                    )
                lintResult1.formattedCode shouldBe expected
            }
        }

        test("autocorrects companion object property") {
            val source = "class C { companion object { val n = 42 } }"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        34,
                        "declare an explicit type on companion object property `n`",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "class C { companion object { val n: Int = 42 } }"
        }

        test("autocorrects object property") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "object O { val n = 42 }"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(1, 16, "declare an explicit type on member property `n`", canBeAutoCorrected = true)
                )
            lintResult1.formattedCode shouldBe "object O { val n: Int = 42 }"
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
                KtLintRuleTestEngine
                    .execute(
                        ruleProvider,
                        source
                    ).diagnostics shouldContainExactlyInAnyOrder
                    listOf(
                        KtLintRuleTestEngine.Diagnostic(
                            1,
                            15,
                            "declare an explicit type on member property `x`",
                            canBeAutoCorrected = false
                        )
                    )
            }
        }

        test("flags only untyped member properties") {
            listOf(
                "fun f() { val x = 42 }",
                "val top = 42",
                "class C { val x: Int = 42 }"
            ).forEach { source ->
                val lintResult1 =
                    KtLintRuleTestEngine.execute(
                        ruleProvider,
                        source
                    )
                lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
                lintResult1.formattedCode shouldBe source
            }
        }

        test("autocorrects property with annotation referencing name") {
            val source = "class C { @Ann(name = \"name\") val name = 42 }"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(1, 35, "declare an explicit type on member property `name`", canBeAutoCorrected = true)
                )
            lintResult1.formattedCode shouldBe "class C { @Ann(name = \"name\") val name: Int = 42 }"
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::ExplicitPropertyType)
    }
}
