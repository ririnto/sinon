package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class NoImportAliasTest :
    FunSpec({
        test("alias matching simple name is flagged") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "import a.Foo as Foo\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        17,
                        "remove the import alias `Foo` that duplicates the imported simple name",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("alias different from simple name is allowed") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import a.Foo as Bar\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "import a.Foo as Bar\n"
        }

        test("import without alias is not flagged") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import a.Foo\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "import a.Foo\n"
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::NoImportAlias)
    }
}
