package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * Verifies the registered rule inventory and its default activation.
 */
class RuleSetProviderTest :
    FunSpec({
        test("registers every harness rule") {
            RuleSetProvider()
                .getRuleProviders()
                .map { provider -> provider.ruleId.value }
                .toSet() shouldBe
                setOf(
                    "harness:companion-object-position",
                    "harness:comparison-direction",
                    "harness:control-flow-braces",
                    "harness:explicit-function-return-type",
                    "harness:explicit-property-type",
                    "harness:explicit-unit-branch",
                    "harness:function-body-blank-lines",
                    "harness:import-over-fqn",
                    "harness:kotest-sibling-spacing",
                    "harness:kotlin-top-level-declaration-count",
                    "harness:kotest-test-assertions",
                    "harness:leading-underscore",
                    "harness:multiline-kdoc",
                    "harness:nested-data-class-last",
                    "harness:no-import-alias",
                    "harness:no-java-path-api",
                    "harness:no-line-comment",
                    "harness:non-null-assertion",
                    "harness:null-comparison-identity",
                    "harness:public-declaration-doc-comment",
                    "harness:slf-direct-logging",
                    "harness:terminal-branch-when",
                    "harness:unchecked-cast-suppression",
                    "harness:unstructured-logging"
                )
        }

        test("preserves a receiver property named it in a parameterless lambda") {
            val source =
                """
                interface Labels {
                    val it: String
                }

                fun label(): Labels.() -> String = { it }
                """.trimIndent() + "\n"
            KtLintRuleEngine(ruleProviders = RuleSetProvider().getRuleProviders())
                .format(Code.fromSnippet(source), rerunAfterAutocorrect = true) { AutocorrectDecision.ALLOW_AUTOCORRECT } shouldBe source
        }

        test("accepts project Regex calls from a sibling file or nullable receiver") {
            val source =
                """
                private class Lookups {
                    fun lookup(factory: Factory?): Record? = factory?.Regex(5)

                    fun lookup(id: Int): Record = Regex(id)
                }
                """.trimIndent()
            val diagnostics = mutableListOf<String>()
            KtLintRuleEngine(ruleProviders = RuleSetProvider().getRuleProviders())
                .lint(Code.fromSnippet(source)) { error -> diagnostics.add(error.detail) }
            diagnostics.shouldBeEmpty()
        }

        test("registers no experimental rule so every rule runs by default") {
            RuleSetProvider()
                .getRuleProviders()
                .map { provider -> provider.createNewRuleInstance() }
                .filterIsInstance<Rule.Experimental>()
                .shouldBeEmpty()
        }

        test("Kotest registration spacing and executable body cleanup converge") {
            val engine =
                KtLintRuleEngine(
                    ruleProviders = setOf(RuleProvider(::FunctionBodyBlankLines), RuleProvider(::KotestSiblingSpacing))
                )
            val formatted =
                engine.format(
                    code =
                        Code.fromSnippet(
                            """
                            import io.kotest.core.spec.style.FunSpec

                            class SampleTest : FunSpec({
                                test("first") {
                                    first()

                                    second()
                                }
                                test("second") {}
                            })
                            """.trimIndent() + "\n"
                        ),
                    rerunAfterAutocorrect = true
                ) { AutocorrectDecision.ALLOW_AUTOCORRECT }
            val remaining: MutableList<String> = mutableListOf()
            engine.lint(Code.fromSnippet(formatted)) { error -> remaining.add(error.detail) }
            assertSoftly {
                formatted shouldBe
                    """
                    import io.kotest.core.spec.style.FunSpec

                    class SampleTest : FunSpec({
                        test("first") {
                            first()
                            second()
                        }

                        test("second") {}
                    })
                    """.trimIndent() + "\n"
                engine.format(Code.fromSnippet(formatted)) { AutocorrectDecision.ALLOW_AUTOCORRECT } shouldBe formatted
                remaining.shouldBeEmpty()
            }
        }

        test("accepts an undocumented public function with all Harness rules enabled") {
            val diagnostics = mutableListOf<String>()
            KtLintRuleEngine(ruleProviders = RuleSetProvider().getRuleProviders())
                .lint(
                    Code.fromSnippet(
                        """
                        /**
                         * Provides an integer answer.
                         */
                        class Probe {
                            fun answer(): Int = 42
                        }
                        """.trimIndent() + "\n"
                    )
                ) { error -> diagnostics.add(error.detail) }
            diagnostics.shouldBeEmpty()
        }

        test("reports an undocumented public class without any configuration") {
            val rules: MutableList<String> = mutableListOf()
            KtLintRuleEngine(ruleProviders = RuleSetProvider().getRuleProviders())
                .lint(Code.fromSnippet("class Service\n")) { error -> rules.add(error.ruleId.value) }
            rules shouldContain "harness:public-declaration-doc-comment"
        }
    })
