package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.nio.file.Path

/**
 * Verifies test assertion diagnostics and their source-set boundary.
 */
class KotestTestAssertionsTest :
    FunSpec({
        test("flags null and Boolean precondition assertions in test sources") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    fun verify(value: String?, condition: Boolean) {
                        requireNotNull(value)
                        checkNotNull(value)
                        require(condition)
                        check(condition)
                    }
                    """.trimIndent() + "\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(2, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(3, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(4, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(5, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false)
                )
            result.formattedCode shouldBe
                """
                fun verify(value: String?, condition: Boolean) {
                    requireNotNull(value)
                    checkNotNull(value)
                    require(condition)
                    check(condition)
                }
                """.trimIndent() + "\n"
        }

        test("flags safe calls whose values are asserted by Kotest matchers") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import io.kotest.matchers.*
                    import io.kotest.matchers.booleans.*
                    import io.kotest.matchers.nulls.*
                    import io.kotest.matchers.shouldBe as shouldEqual

                    fun verify(value: String?, condition: Boolean?) {
                        value?.length shouldBe 1
                        (value?.length) shouldBe 1
                        value?.shouldNotBeNull()
                        condition?.shouldBeTrue()
                        value?.shouldEqual("value")
                        value?.let { item -> item shouldBe "value" }
                        value?.length
                    }
                    """.trimIndent() + "\n",
                    sourcePath = Path.of("src/testFixtures/kotlin/SampleTest.kt")
                )
            result.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(7, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(8, 6, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(9, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(10, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(11, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false)
                )
        }

        test("flags aliases of standard-library assertion functions") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import kotlin.require as assertCondition

                    fun verify(condition: Boolean) {
                        assertCondition(condition)
                    }
                    """.trimIndent() + "\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics.map { diagnostic -> diagnostic.ruleId } shouldContainExactlyInAnyOrder
                listOf("harness:kotest-test-assertions")
        }

        test("flags assertions in the androidTest source set") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "fun verify(condition: Boolean) = check(condition)\n",
                    sourcePath = Path.of("src/androidTest/kotlin/SampleTest.kt")
                )
            result.diagnostics.map { diagnostic -> diagnostic.ruleId } shouldContainExactlyInAnyOrder
                listOf("harness:kotest-test-assertions")
        }

        test("does not treat safe-qualified selectors as standard-library calls") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import kotlin.check\nfun sample(validator: Any?, condition: Boolean) { validator?.check(condition) }\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics shouldContainExactlyInAnyOrder emptyList()
        }

        test("leaves production code and non-asserting test safe calls unchanged") {
            val sources =
                listOf(
                    "src/main/kotlin/SampleTest.kt" to "fun sample(value: String?) = requireNotNull(value)\n",
                    "src/main/kotlin/TestSupport.kt" to "fun sample(value: String?) = requireNotNull(value)\n",
                    "testing/src/main/kotlin/Sample.kt" to "fun sample(value: String?) = requireNotNull(value)\n",
                    "/workspace/test-project/src/main/kotlin/Sample.kt" to "fun sample(value: String?) = requireNotNull(value)\n",
                    "src/test/kotlin/SampleTest.kt" to "fun sample(value: String?) = value?.length\n",
                    "src/test/kotlin/SampleTest.kt" to
                        "class Validator { fun check(condition: Boolean) = condition }\nfun sample(validator: Validator?) { validator?.check(true) }\n",
                    "src/test/kotlin/SampleTest.kt" to
                        "fun sample(value: String?) {\n    fun String?.shouldBe(expected: Int) = Unit\n    value?.shouldBe(1)\n}\n",
                    "src/test/kotlin/SampleTest.kt" to
                        "fun sample(value: String?) {\n    fun requireNotNull(value: String?) = value\n    requireNotNull(value)\n}\n"
                )
            sources.forEach { (path, source) ->
                KtLintRuleTestEngine.execute(ruleProvider, source, sourcePath = Path.of(path)).diagnostics shouldContainExactlyInAnyOrder
                    emptyList()
            }
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::KotestTestAssertions)
    }
}
