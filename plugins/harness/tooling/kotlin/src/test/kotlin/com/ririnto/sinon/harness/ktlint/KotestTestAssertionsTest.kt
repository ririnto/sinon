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

        test("flags Kotlin assert calls including qualified and aliased calls") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import kotlin.assert as assertCondition

                    fun verify(condition: Boolean) {
                        assert(condition)
                        assertCondition(condition)
                        kotlin.assert(condition)
                    }
                    """.trimIndent() + "\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(4, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(5, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(6, 12, "harness:kotest-test-assertions", canBeAutoCorrected = false)
                )
        }

        test("ignores inaccessible outer declarations when resolving standard assertions") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import kotlin.requireNotNull

                    class Outer {
                        fun requireNotNull(value: String?) = value

                        class NestedTest {
                            fun verify(value: String?) { requireNotNull(value) }
                        }
                    }
                    """.trimIndent() + "\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics.map { diagnostic -> diagnostic.ruleId } shouldContainExactlyInAnyOrder
                listOf("harness:kotest-test-assertions")
        }

        test("leaves package-qualified assertions shadowed by inherited kotlin properties") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import kotlin.test.assertTrue

                    class PackageLike {
                        val test: PackageLike = this
                        fun assertTrue(condition: Boolean) = Unit
                    }

                    open class PackageBase(val kotlin: PackageLike)

                    class QualifiedShadowTest : PackageBase(PackageLike()) {
                        fun verify(condition: Boolean) { kotlin.test.assertTrue(condition) }
                    }
                    """.trimIndent() + "\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics shouldContainExactlyInAnyOrder emptyList()
        }

        test("ignores inaccessible outer kotlin declarations in qualified receivers") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import kotlin.test.assertTrue

                    class PackageLike {
                        val test: PackageLike = this
                        fun assertTrue(condition: Boolean) = Unit
                    }

                    class Outer {
                        val kotlin: PackageLike = PackageLike()

                        class NestedTest {
                            fun verify(condition: Boolean) { kotlin.test.assertTrue(condition) }
                        }
                    }
                    """.trimIndent() + "\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics.map { diagnostic -> diagnostic.ruleId } shouldContainExactlyInAnyOrder
                listOf("harness:kotest-test-assertions")
        }

        test("respects inherited standard assertion names and private visibility") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import kotlin.requireNotNull

                    open class AssertionBase {
                        fun requireNotNull(value: String?) = value
                        private fun check(condition: Boolean) = Unit
                    }

                    class DerivedTest : AssertionBase() {
                        fun verify(value: String?, condition: Boolean) {
                            requireNotNull(value)
                            check(condition)
                        }
                    }
                    """.trimIndent() + "\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics shouldContainExactlyInAnyOrder
                listOf(KtLintRuleTestEngine.Diagnostic(11, 9, "harness:kotest-test-assertions", canBeAutoCorrected = false))
        }

        test("flags kotlin.test Boolean assertions through imports, aliases, and package qualifiers") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import kotlin.test.assertFalse
                    import kotlin.test.assertTrue as verifyCondition

                    fun verify(condition: Boolean, inverted: Boolean) {
                        assertFalse(inverted)
                        verifyCondition(condition)
                        kotlin.test.assertTrue(condition)
                        kotlin.test.assertFalse { inverted }
                    }

                    fun verifyWithLocalShadow(condition: Boolean) {
                        fun assertTrue(condition: Boolean) = Unit
                        assertTrue(condition)
                        kotlin.test.assertTrue(condition)
                    }
                    """.trimIndent() + "\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics.map { diagnostic -> diagnostic.detail } shouldContainExactlyInAnyOrder
                listOf(
                    "use Kotest `shouldBeFalse()` on the asserted condition",
                    "use Kotest `shouldBeTrue()` on the asserted condition",
                    "use Kotest `shouldBeTrue()` on the asserted condition",
                    "use Kotest `shouldBeFalse()` on the asserted condition",
                    "use Kotest `shouldBeTrue()` on the asserted condition"
                )
            result.diagnostics.all { diagnostic -> !diagnostic.canBeAutoCorrected } shouldBe true
        }

        test("flags kotlin.test assertNotNull through imports, aliases, and package qualifiers") {
            val imported =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import kotlin.test.assertNotNull\nfun verify(value: String?) { assertNotNull(value); kotlin.test.assertNotNull(value) }\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            imported.diagnostics.map { diagnostic -> diagnostic.detail } shouldContainExactlyInAnyOrder
                listOf(
                    "use Kotest `shouldNotBeNull()` for null assertions",
                    "use Kotest `shouldNotBeNull()` for null assertions"
                )
            imported.diagnostics.all { diagnostic -> !diagnostic.canBeAutoCorrected } shouldBe true
            val aliased =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import kotlin.test.assertNotNull as verifyNotNull\nfun verify(value: String?) { verifyNotNull(value) }\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            aliased.diagnostics.map { diagnostic -> diagnostic.detail } shouldContainExactlyInAnyOrder
                listOf("use Kotest `shouldNotBeNull()` for null assertions")
        }

        test("flags kotlin.test assertNotNull through a wildcard import") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import kotlin.test.*\nfun verify(value: String?) { assertNotNull(value) }\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics.map { diagnostic -> diagnostic.ruleId } shouldContainExactlyInAnyOrder
                listOf("harness:kotest-test-assertions")
            val ambiguous =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import kotlin.test.*\nimport example.*\nfun verify(value: String?) { assertNotNull(value) }\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            ambiguous.diagnostics shouldContainExactlyInAnyOrder emptyList()
        }

        test("leaves shadowed and production assertNotNull calls unchanged") {
            val shadowed =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import kotlin.test.assertNotNull\nfun verify(value: String?) {\n    fun assertNotNull(value: String?) = Unit\n    assertNotNull(value)\n}\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            shadowed.diagnostics shouldContainExactlyInAnyOrder emptyList()
            val production =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import kotlin.test.assertNotNull\nfun verify(value: String?) { assertNotNull(value) }\n",
                    sourcePath = Path.of("src/main/kotlin/Sample.kt")
                )
            production.diagnostics shouldContainExactlyInAnyOrder emptyList()
        }

        test("flags kotlin.test Boolean assertions imported by wildcard") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import kotlin.test.*\nfun verify(condition: Boolean) { assertTrue(condition) }\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics.map { diagnostic -> diagnostic.ruleId } shouldContainExactlyInAnyOrder
                listOf("harness:kotest-test-assertions")
        }

        test("leaves wildcard assertion names ambiguous with another package wildcard") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import kotlin.test.*\nimport example.*\nfun verify() { assertTrue(\"ready\") }\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics shouldContainExactlyInAnyOrder emptyList()
        }

        test("flags imported assertions inside nested classes without outer instance access") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import kotlin.test.assertTrue

                    class Outer {
                        fun assertTrue(condition: Boolean) = Unit

                        class NestedTest {
                            fun verify(condition: Boolean) {
                                assertTrue(condition)
                                val anonymous = object {
                                    fun run() { assertTrue(condition) }
                                }
                            }
                        }
                    }

                    fun holder(assertTrue: (Boolean) -> Unit) {
                        class LocalTest {
                            fun verify() { assertTrue(true) }
                        }
                    }
                    """.trimIndent() + "\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics.map { diagnostic -> diagnostic.detail } shouldContainExactlyInAnyOrder
                listOf(
                    "use Kotest `shouldBeTrue()` on the asserted condition",
                    "use Kotest `shouldBeTrue()` on the asserted condition"
                )
        }

        test("accounts for inherited members when resolving Kotlin test assertions") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    import external.Base
                    import kotlin.test.assertTrue

                    open class PrivateBase {
                        private fun assertTrue(condition: Boolean) = Unit
                    }

                    open class PropertyBase(val assertTrue: (Boolean) -> Unit)

                    open class PrivatePropertyBase(private val assertTrue: (Boolean) -> Unit)

                    class PrivateMemberTest : PrivateBase() {
                        fun verify(condition: Boolean) { assertTrue(condition) }
                    }

                    class InheritedPropertyTest : PropertyBase({}) {
                        fun verify(condition: Boolean) { assertTrue(condition) }
                    }

                    class PrivateInheritedPropertyTest : PrivatePropertyBase({}) {
                        fun verify(condition: Boolean) { assertTrue(condition) }
                    }

                    class ExternalBaseTest : external.Base() {
                        fun verify(condition: Boolean) { assertTrue(condition) }
                    }

                    class ImportedExternalBaseTest : Base() {
                        fun verify(condition: Boolean) { assertTrue(condition) }
                    }

                    class ExternalBaseOuterTest : external.Base() {
                        inner class NestedTest {
                            fun verify(condition: Boolean) { assertTrue(condition) }
                        }
                    }

                    open class ObjectBase {
                        fun assertTrue(condition: Boolean) = Unit
                    }

                    class ObjectExpressionTest : ObjectBase() {
                        fun verify(condition: Boolean) {
                            val anonymous = object {
                                fun run() { assertTrue(condition) }
                            }
                        }
                    }

                    class QualifiedExternalBaseTest : external.Base() {
                        fun verify(condition: Boolean) { kotlin.test.assertTrue(condition) }
                    }
                    """.trimIndent() + "\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(13, 38, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(21, 38, "harness:kotest-test-assertions", canBeAutoCorrected = false)
                )
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
                    import example.*
                    import kotlin.assert as assertBoolean
                    import kotlin.check as checkBoolean
                    import kotlin.checkNotNull as checkValue
                    import kotlin.require as assertCondition
                    import kotlin.requireNotNull as requireValue

                    fun verify(value: String?, condition: Boolean) {
                        assertBoolean(condition)
                        checkBoolean(condition)
                        checkValue(value)
                        assertCondition(condition)
                        requireValue(value)
                    }
                    """.trimIndent() + "\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(9, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(10, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(11, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(12, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(13, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false)
                )
        }

        test("flags safe calls asserted by direct and wildcard Kotest should matchers") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import io.kotest.matchers.collections.shouldHaveSize\nfun verify(items: List<String>?) { items?.shouldHaveSize(1) }\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            val wildcardResult =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import io.kotest.matchers.collections.*\nfun verify(items: List<String>?) { items?.shouldHaveSize(1) }\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            val ruleIds =
                listOf(result, wildcardResult)
                    .flatMap { matcherResult -> matcherResult.diagnostics }
                    .map { diagnostic -> diagnostic.ruleId }
            ruleIds shouldContainExactlyInAnyOrder
                listOf("harness:kotest-test-assertions", "harness:kotest-test-assertions")
        }

        test("prefers an explicit non-Kotest matcher import over a Kotest wildcard") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import io.kotest.matchers.collections.*\nimport example.shouldHaveSize\nfun verify(items: List<String>?) { items?.shouldHaveSize(1) }\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics shouldContainExactlyInAnyOrder emptyList()
        }

        test("leaves standard-library assertion names unresolved with a custom wildcard import") {
            listOf(
                "import example.*",
                "import kotlin.*\nimport example.*",
                "import kotlin.test.*\nimport example.*"
            ).forEach { imports ->
                val source =
                    """
                    fun verify(value: String?, condition: Boolean) {
                        assert(condition)
                        check(condition)
                        require(condition)
                        checkNotNull(value)
                        requireNotNull(value)
                    }
                    """.trimIndent() + "\n"
                val result =
                    KtLintRuleTestEngine.execute(
                        ruleProvider,
                        "$imports\n$source",
                        sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                    )
                result.diagnostics shouldContainExactlyInAnyOrder emptyList()
                result.formattedCode shouldBe "$imports\n$source"
            }
        }

        test("flags standard-library assertions with a Kotlin test wildcard import") {
            val source =
                """
                import kotlin.test.*

                fun verify(value: String?, condition: Boolean) {
                    assert(condition)
                    check(condition)
                    require(condition)
                    checkNotNull(value)
                    requireNotNull(value)
                }
                """.trimIndent() + "\n"
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source,
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(4, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(5, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(6, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(7, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false),
                    KtLintRuleTestEngine.Diagnostic(8, 5, "harness:kotest-test-assertions", canBeAutoCorrected = false)
                )
            result.formattedCode shouldBe source
        }

        test("flags explicit and qualified standard-library assertions despite a custom wildcard import") {
            listOf(
                """
                import example.*
                import kotlin.assert
                import kotlin.check
                import kotlin.checkNotNull
                import kotlin.require
                import kotlin.requireNotNull

                fun verify(value: String?, condition: Boolean) {
                    assert(condition)
                    check(condition)
                    require(condition)
                    checkNotNull(value)
                    requireNotNull(value)
                }
                """.trimIndent() + "\n",
                """
                import example.*

                fun verify(value: String?, condition: Boolean) {
                    kotlin.assert(condition)
                    kotlin.check(condition)
                    kotlin.require(condition)
                    kotlin.checkNotNull(value)
                    kotlin.requireNotNull(value)
                }
                """.trimIndent() + "\n"
            ).forEach { source ->
                val result =
                    KtLintRuleTestEngine.execute(
                        ruleProvider,
                        source,
                        sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                    )
                result.diagnostics.map { diagnostic -> diagnostic.detail } shouldContainExactlyInAnyOrder
                    listOf(
                        "use Kotest `shouldBeTrue()` on the asserted condition",
                        "use Kotest `shouldBeTrue()` on the asserted condition",
                        "use Kotest `shouldBeTrue()` on the asserted condition",
                        "use Kotest `shouldNotBeNull()` for null assertions",
                        "use Kotest `shouldNotBeNull()` for null assertions"
                    )
                result.diagnostics.all { diagnostic -> !diagnostic.canBeAutoCorrected } shouldBe true
                result.formattedCode shouldBe source
            }
        }

        test("flags standard-library assertions with a Kotlin wildcard import") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "import kotlin.*\nfun verify(value: String?, condition: Boolean) {\n    requireNotNull(value)\n    kotlin.require(condition)\n}\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics.map { diagnostic -> diagnostic.ruleId } shouldContainExactlyInAnyOrder
                listOf("harness:kotest-test-assertions", "harness:kotest-test-assertions")
        }

        test("recognizes the Kotlin package when an imported name is aliased away") {
            val result =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    "package example\n\nimport example.other.kotlin as guards\n\nobject other { object kotlin { fun require(condition: Boolean) = Unit } }\nfun sample(condition: Boolean) { kotlin.require(condition) }\n",
                    sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                )
            result.diagnostics.map { diagnostic -> diagnostic.ruleId } shouldContainExactlyInAnyOrder
                listOf("harness:kotest-test-assertions")
        }

        test("ignores imported names shadowed by parameters and local matcher declarations") {
            val sources =
                listOf(
                    "import kotlin.require as assertCondition\nfun verify(assertCondition: (Boolean) -> Unit, condition: Boolean) { assertCondition(condition) }\n",
                    """
                    import io.kotest.matchers.shouldBe

                    fun verify(value: String?) {
                        fun String?.shouldBe(expected: Int) = Unit
                        value?.shouldBe(1)
                    }
                    """.trimIndent() + "\n"
                )
            sources.forEach { source ->
                val result =
                    KtLintRuleTestEngine.execute(
                        ruleProvider,
                        source,
                        sourcePath = Path.of("src/test/kotlin/SampleTest.kt")
                    )
                result.diagnostics shouldContainExactlyInAnyOrder emptyList()
            }
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
                        "class Validator { fun require(condition: Boolean) = Unit }\nfun sample(kotlin: Validator, condition: Boolean) { kotlin.require(condition) }\n",
                    "src/test/kotlin/SampleTest.kt" to
                        "package example\n\nimport example.Guards as kotlin\n\nobject Guards { fun require(condition: Boolean) = Unit }\nfun sample(condition: Boolean) { kotlin.require(condition) }\n",
                    "src/test/kotlin/SampleTest.kt" to
                        "fun sample(value: String?) {\n    fun String?.shouldBe(expected: Int) = Unit\n    value?.shouldBe(1)\n}\n",
                    "src/test/kotlin/SampleTest.kt" to
                        "fun sample(value: String?) {\n    fun requireNotNull(value: String?) = value\n    requireNotNull(value)\n}\n",
                    "src/test/kotlin/SampleTest.kt" to
                        "fun sample(assert: (Boolean) -> Unit, condition: Boolean) { assert(condition) }\n",
                    "src/test/kotlin/SampleTest.kt" to
                        "import example.assert\nfun sample(condition: Boolean) = assert(condition)\n",
                    "src/test/kotlin/SampleTest.kt" to
                        "import kotlin.test.*\nfun sample(assertTrue: (Boolean) -> Unit, condition: Boolean) { assertTrue(condition) }\n",
                    "src/test/kotlin/SampleTest.kt" to
                        "import kotlin.test.*\nimport example.assertTrue\nfun sample(condition: Boolean) { assertTrue(condition) }\n",
                    "src/test/kotlin/SampleTest.kt" to
                        "import kotlin.test.*\nfun sample(kotlin: TestApi, condition: Boolean) { kotlin.test.assertTrue(condition) }\n",
                    "src/test/kotlin/SampleTest.kt" to
                        "import kotlin.test.assertTrue\nopen class Base { fun assertTrue(condition: Boolean) = Unit }\nclass SampleTest : Base() { fun verify(condition: Boolean) { assertTrue(condition) } }\n",
                    "src/main/kotlin/SampleTest.kt" to
                        "import kotlin.test.assertTrue\nfun sample(condition: Boolean) { assertTrue(condition) }\n",
                    "/workspace/src/test-project/repo/src/main/kotlin/SampleTest.kt" to
                        "import kotlin.test.assertTrue\nfun sample(condition: Boolean) { assertTrue(condition) }\n",
                    "test-project/SampleTest.kt" to
                        "import kotlin.test.assertTrue\nfun sample(condition: Boolean) { assertTrue(condition) }\n",
                    "src/main/kotlin/SampleTest.kt" to "fun sample(condition: Boolean) = assert(condition)\n"
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
