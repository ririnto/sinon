package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ExplicitFunctionReturnTypeTest :
    FunSpec({
        test("autocorrects bare literals") {
            val cases =
                listOf(
                    "fun greeting() = \"hi\"" to "fun greeting(): String = \"hi\"",
                    "fun flag() = true" to "fun flag(): Boolean = true",
                    "fun first() = 'a'" to "fun first(): Char = 'a'",
                    "fun count() = 42" to "fun count(): Int = 42",
                    "fun overflow() = 3000000000" to "fun overflow(): Long = 3000000000",
                    "fun bigCount() = 42L" to "fun bigCount(): Long = 42L",
                    "fun ratio() = 3.14" to "fun ratio(): Double = 3.14",
                    "fun precise() = 3.14f" to "fun precise(): Float = 3.14f"
                )
            cases.forEach { (sourceLine, expectedLine) ->
                val source = "$sourceLine\n"
                val name = sourceLine.substringAfter("fun ").substringBefore("(")
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
                            "declare an explicit return type on named function `$name`",
                            canBeAutoCorrected = true
                        )
                    )
                lintResult1.formattedCode shouldBe "$expectedLine\n"
            }
        }

        test("leaves unsafe shapes lint only") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "fun compute() = calculate()\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        5,
                        "declare an explicit return type on named function `compute`",
                        canBeAutoCorrected = false
                    )
                )
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "fun neg() = -1\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        5,
                        "declare an explicit return type on named function `neg`",
                        canBeAutoCorrected = false
                    )
                )
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "fun composed() = if (b) 1 else 2\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        5,
                        "declare an explicit return type on named function `composed`",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("leaves override functions lint only") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "override fun name() = \"x\"\n"
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
            val source = "fun already(): Int = 42\nfun block() { val x = 1 }\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("ignores explicit unit expression body") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun noop() = Unit\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "fun noop() = Unit\n"
        }

        test("ignores explicit kotlin unit expression body") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun noop() = kotlin.Unit\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "fun noop() = kotlin.Unit\n"
        }

        test("strips explicit unit return type from expression body") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun noop(): Unit = Unit\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        13,
                        "omit the redundant `Unit` return type on named function `noop`",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "fun noop() = Unit\n"
        }

        test("strips explicit kotlin unit return type") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun noop(): kotlin.Unit {}\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        13,
                        "omit the redundant `Unit` return type on named function `noop`",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "fun noop() {}\n"
        }

        test("ignores qualified non kotlin unit return type") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun noop(): other.kotlin.Unit {}\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "fun noop(): other.kotlin.Unit {}\n"
        }

        test("accepts load bearing unit return type on expression bodied call") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun clear(): Unit = delegate.clear()\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "fun clear(): Unit = delegate.clear()\n"
        }

        test("accepts load bearing kotlin unit return type on expression bodied call") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun clear(): kotlin.Unit = delegate.clear()\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "fun clear(): kotlin.Unit = delegate.clear()\n"
        }

        test("still requires explicit type on untyped expression bodied unit call") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "fun clear() = delegate.clear()\n"
                ).diagnostics shouldContainExactlyInAnyOrder
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
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun noop(): Unit { println(\"hi\") }\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        13,
                        "omit the redundant `Unit` return type on named function `noop`",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "fun noop() { println(\"hi\") }\n"
        }

        test("autocorrects function with default parameter") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun greet(prefix: String = \"hi\") = \"hello\"\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        5,
                        "declare an explicit return type on named function `greet`",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "fun greet(prefix: String = \"hi\"): String = \"hello\"\n"
        }

        test("autocorrects function with annotation argument") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "@Ann(enabled = true)\nfun greet() = \"hello\"\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        5,
                        "declare an explicit return type on named function `greet`",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "@Ann(enabled = true)\nfun greet(): String = \"hello\"\n"
        }

        test("strips unit return type preserving whitespace before colon") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun noop() : Unit {}\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        14,
                        "omit the redundant `Unit` return type on named function `noop`",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "fun noop() {}\n"
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::ExplicitFunctionReturnType)
    }
}
