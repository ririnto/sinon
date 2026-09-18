@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class RegexConstructorTest {
    private val assertThat = assertThatRule { RegexConstructor() }

    @Test
    fun autocorrectsOnePositionalStringTemplateExpression() {
        assertThat("val pattern = Regex(\"a+\")\n")
            .hasLintViolation(1, 15, "avoid `Regex(...)` constructor; use `String.toRegex()` instead")
            .isFormattedAs("val pattern = \"a+\".toRegex()\n")
    }

    @Test
    fun autocorrectsRegexConstructorUsedAsReceiver() {
        assertThat("val matched = Regex(\"a+\").matches(\"aaa\")\n")
            .hasLintViolation(1, 15, "avoid `Regex(...)` constructor; use `String.toRegex()` instead")
            .isFormattedAs("val matched = \"a+\".toRegex().matches(\"aaa\")\n")
    }

    @Test
    fun autocorrectsRegexConstructorUsedAsArgument() {
        assertThat("val result = listOf(Regex(\"a+\"))\n")
            .hasLintViolation(1, 21, "avoid `Regex(...)` constructor; use `String.toRegex()` instead")
            .isFormattedAs("val result = listOf(\"a+\".toRegex())\n")
    }

    @Test
    fun leavesUnsafeConstructorShapesUnchanged() {
        val source =
            """
            fun build(pattern: String, option: RegexOption): Regex {
                val first = Regex(pattern)
                val second = Regex(pattern = "a+")
                val third = Regex("a+", option)
                return Regex(makePattern())
            }
            """.trimIndent() + "\n"
        assertThat(source).hasLintViolationsWithoutAutoCorrect(
            LintViolation(2, 17, "avoid `Regex(...)` constructor; use `String.toRegex()` instead"),
            LintViolation(3, 18, "avoid `Regex(...)` constructor; use `String.toRegex()` instead"),
            LintViolation(4, 17, "avoid `Regex(...)` constructor; use `String.toRegex()` instead"),
            LintViolation(5, 12, "avoid `Regex(...)` constructor; use `String.toRegex()` instead")
        )
    }

    @Test
    fun qualifiedReceiverRegexIsSafe() {
        val source = "class Factory\nfun build(factory: Factory) = factory.Regex(\"a+\")\n"
        assertThat(source).hasNoLintViolations()
    }

    @Test
    fun autocorrectsFullyQualifiedKotlinTextRegexCall() {
        assertThat("val pattern = kotlin.text.Regex(\"a+\")\n")
            .hasLintViolation(1, 27, "avoid `Regex(...)` constructor; use `String.toRegex()` instead")
            .isFormattedAs("val pattern = \"a+\".toRegex()\n")
    }

    @Test
    fun explicitQualificationBypassesNameConflictSuppression() {
        val source =
            """
            import com.example.Regex
            val suppressed = Regex("a+")
            val explicit = kotlin.text.Regex("a+")
            """.trimIndent() + "\n"
        val expected =
            """
            import com.example.Regex
            val suppressed = Regex("a+")
            val explicit = "a+".toRegex()
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolation(3, 28, "avoid `Regex(...)` constructor; use `String.toRegex()` instead")
            .isFormattedAs(expected)
    }
}
