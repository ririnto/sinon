package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class ImportOverFqnTest :
    FunSpec({
        test("simple fqn is rewritten and imported") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "val value = kotlin.collections.ArrayList<String>()\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        13,
                        "fully qualified name `kotlin.collections.ArrayList` used inline; add an import and use the simple name",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "import kotlin.collections.ArrayList\n\nval value = ArrayList<String>()\n"
        }

        test("multiple packages add multiple imports") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "val a = java.util.ArrayList<String>()\nval b = kotlin.collections.LinkedList<String>()\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
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
            lintResult1.formattedCode shouldBe
                "import java.util.ArrayList\nimport kotlin.collections.LinkedList\n\nval a = ArrayList<String>()\nval b = LinkedList<String>()\n"
        }

        test("inserts new import in alphabetical order with existing imports") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import gamma.delta.Baz\n\nval value = alpha.beta.Foo()\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        13,
                        "fully qualified name `alpha.beta.Foo` used inline; add an import and use the simple name",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "import alpha.beta.Foo\nimport gamma.delta.Baz\n\nval value = Foo()\n"
        }

        test("local name collision is lint only") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "fun test() { val ArrayList = 1; println(java.util.ArrayList<String>()) }\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        41,
                        "fully qualified name `java.util.ArrayList` used inline; add an import and use the simple name",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("same name import is lint only") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "import other.ArrayList\nval value = java.util.ArrayList<String>()\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        13,
                        "fully qualified name `java.util.ArrayList` used inline; add an import and use the simple name",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("same path import allows shortening") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import kotlin.collections.ArrayList\n\nval value = kotlin.collections.ArrayList<String>()\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        13,
                        "fully qualified name `kotlin.collections.ArrayList` used inline; add an import and use the simple name",
                        canBeAutoCorrected = true
                    )
                )
            lintResult1.formattedCode shouldBe "import kotlin.collections.ArrayList\n\nval value = ArrayList<String>()\n"
        }

        test("same path alias import does not allow shortening") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "import kotlin.collections.ArrayList as JList\n\nval value = kotlin.collections.ArrayList<String>()\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        13,
                        "fully qualified name `kotlin.collections.ArrayList` used inline; add an import and use the simple name",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("distinct fqns with same simple name stay lint only") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "val first = alpha.one.Widget()\nval second = beta.two.Widget()\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        1,
                        13,
                        "fully qualified name `alpha.one.Widget` used inline; add an import and use the simple name",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        14,
                        "fully qualified name `beta.two.Widget` used inline; add an import and use the simple name",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("own package is lint only") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "package java.util\n\nval value = java.util.ArrayList<String>()\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        13,
                        "fully qualified name `java.util.ArrayList` used inline; add an import and use the simple name",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("star import conflict is lint only") {
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    "import other.*\nval value = java.util.ArrayList<String>()\n"
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        2,
                        13,
                        "fully qualified name `java.util.ArrayList` used inline; add an import and use the simple name",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("already short name is no op") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "val value = ArrayList<String>()\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe "val value = ArrayList<String>()\n"
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::ImportOverFqn)
    }
}
