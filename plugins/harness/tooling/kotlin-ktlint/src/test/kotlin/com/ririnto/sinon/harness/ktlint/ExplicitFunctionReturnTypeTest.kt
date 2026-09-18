@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class ExplicitFunctionReturnTypeTest {
    private val assertThat = assertThatRule { ExplicitFunctionReturnType() }

    @Test
    fun autocorrectsBareLiterals() {
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
            assertThat(source)
                .hasLintViolation(1, offset, "declare an explicit return type on named function `$name`")
                .isFormattedAs("$expectedLine\n")
        }
    }

    @Test
    fun leavesUnsafeShapesLintOnly() {
        assertThat("fun compute() = calculate()\n")
            .hasLintViolationWithoutAutoCorrect(1, 5, "declare an explicit return type on named function `compute`")
        assertThat("fun neg() = -1\n")
            .hasLintViolationWithoutAutoCorrect(1, 5, "declare an explicit return type on named function `neg`")
        assertThat("fun composed() = if (b) 1 else 2\n")
            .hasLintViolationWithoutAutoCorrect(1, 5, "declare an explicit return type on named function `composed`")
    }

    @Test
    fun leavesOverrideFunctionsLintOnly() {
        assertThat("override fun name() = \"x\"\n")
            .hasLintViolationWithoutAutoCorrect(1, 14, "declare an explicit return type on named function `name`")
    }

    @Test
    fun ignoresAlreadyTypedAndBlockFunctions() {
        val source = "fun already(): Int = 42\nfun block() { val x = 1 }\n"
        assertThat(source).hasNoLintViolations()
    }

    @Test
    fun ignoresExplicitUnitExpressionBody() {
        assertThat("fun noop() = Unit\n").hasNoLintViolations()
    }

    @Test
    fun ignoresExplicitKotlinUnitExpressionBody() {
        assertThat("fun noop() = kotlin.Unit\n").hasNoLintViolations()
    }

    @Test
    fun stripsExplicitUnitReturnTypeFromExpressionBody() {
        assertThat("fun noop(): Unit = Unit\n")
            .hasLintViolation(1, 13, "omit the redundant `Unit` return type on named function `noop`")
            .isFormattedAs("fun noop() = Unit\n")
    }

    @Test
    fun stripsExplicitKotlinUnitReturnType() {
        assertThat("fun noop(): kotlin.Unit {}\n")
            .hasLintViolation(1, 13, "omit the redundant `Unit` return type on named function `noop`")
            .isFormattedAs("fun noop() {}\n")
    }

    @Test
    fun ignoresQualifiedNonKotlinUnitReturnType() {
        assertThat("fun noop(): other.kotlin.Unit {}\n").hasNoLintViolations()
    }

    @Test
    fun acceptsLoadBearingUnitReturnTypeOnExpressionBodiedCall() {
        assertThat("fun clear(): Unit = delegate.clear()\n").hasNoLintViolations()
    }

    @Test
    fun acceptsLoadBearingKotlinUnitReturnTypeOnExpressionBodiedCall() {
        assertThat("fun clear(): kotlin.Unit = delegate.clear()\n").hasNoLintViolations()
    }

    @Test
    fun stillRequiresExplicitTypeOnUntypedExpressionBodiedUnitCall() {
        assertThat("fun clear() = delegate.clear()\n")
            .hasLintViolationWithoutAutoCorrect(1, 5, "declare an explicit return type on named function `clear`")
    }

    @Test
    fun stripsExplicitUnitReturnTypeFromBlockBody() {
        assertThat("fun noop(): Unit { println(\"hi\") }\n")
            .hasLintViolation(1, 13, "omit the redundant `Unit` return type on named function `noop`")
            .isFormattedAs("fun noop() { println(\"hi\") }\n")
    }

    @Test
    fun autocorrectsFunctionWithDefaultParameter() {
        assertThat("fun greet(prefix: String = \"hi\") = \"hello\"\n")
            .hasLintViolation(1, 5, "declare an explicit return type on named function `greet`")
            .isFormattedAs("fun greet(prefix: String = \"hi\"): String = \"hello\"\n")
    }

    @Test
    fun autocorrectsFunctionWithAnnotationArgument() {
        assertThat("@Ann(enabled = true)\nfun greet() = \"hello\"\n")
            .hasLintViolation(2, 5, "declare an explicit return type on named function `greet`")
            .isFormattedAs("@Ann(enabled = true)\nfun greet(): String = \"hello\"\n")
    }

    @Test
    fun stripsUnitReturnTypePreservingWhitespaceBeforeColon() {
        assertThat("fun noop() : Unit {}\n")
            .hasLintViolation(1, 14, "omit the redundant `Unit` return type on named function `noop`")
            .isFormattedAs("fun noop() {}\n")
    }
}
