package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class NoLineCommentTest :
    FunSpec({
        test("standalone line comment is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "fun foo() {\n    // comment\n}\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        5,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("trailing line comment is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "fun foo() {\n    val x = 1 // comment\n}\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        15,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("block comment is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "fun foo() {\n    /* block */\n}\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        5,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("mixed line and block comments are all flagged") {
            val source = "fun foo() {\n    // one\n    val x = 1 // two\n    /* three */ val y = 2\n}\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        5,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        15,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        5,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("kdoc comment is not flagged") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "/** docs */\nfun foo()\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "/** docs */\nfun foo()\n"
        }

        test("comment at top of file is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "// file-level comment\nfun foo()\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        1,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("line comment inside lambda body is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "fun foo() {\n    listOf(1).forEach {\n        // comment\n        it.inc()\n    }\n}\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        9,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("comment markers inside raw string are ignored") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "val text = \"\"\"// not a comment /* also not a comment */\"\"\"\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "val text = \"\"\"// not a comment /* also not a comment */\"\"\"\n"
        }

        test("format leaves commented source unchanged") {
            val source = "fun foo() {\n    // comment\n}\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        5,
                        "use KDoc (/** ... */) instead of // or /* */ comments",
                        canBeAutoCorrected = false
                    )
                )
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::NoLineComment)
    }
}
