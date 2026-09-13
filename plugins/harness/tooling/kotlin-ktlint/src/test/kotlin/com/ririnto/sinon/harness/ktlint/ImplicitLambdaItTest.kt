package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class ImplicitLambdaItTest {
    private val assertThat = assertThatRule { ImplicitLambdaIt() }

    @Test
    fun namesImplicitParameterAndPreservesEscapes() {
        val source =
            """
            fun render(items: List<String>) = items.joinToString { "\\n${'$'}it" }
            """.trimIndent() + "\n"
        val expected =
            """
            fun render(items: List<String>) = items.joinToString { value -> "\\n${'$'}value" }
            """.trimIndent() + "\n"
        val itOffset = source.indexOf("it", source.indexOf('{'))
        assertThat(source)
            .hasLintViolation(1, itOffset + 1, "use an explicit name for the implicit `it` lambda parameter")
            .isFormattedAs(expected)
    }

    @Test
    fun preservesOuterImplicitCaptureInsideNestedExplicitLambda() {
        val source =
            """
            fun nested(values: List<String>) = values.map { it.let { item -> it + item } }
            """.trimIndent() + "\n"
        val expected =
            """
            fun nested(values: List<String>) = values.map { value -> value.let { item -> value + item } }
            """.trimIndent() + "\n"
        val itOffset = source.indexOf("it", source.indexOf('{'))
        assertThat(source)
            .hasLintViolation(1, itOffset + 1, "use an explicit name for the implicit `it` lambda parameter")
            .isFormattedAs(expected)
    }

    @Test
    fun reportsOnlyTheLambdaWithAnImplicitParameter() {
        val source =
            """
            fun nested(values: List<List<Int>>) = values.map {
                it.flatMap { inner -> inner + it.size }
            }
            """.trimIndent() + "\n"
        val itOffset = source.indexOf("it", source.indexOf('{'))
        assertThat(
            source
        ).hasLintViolation(2, itOffset - source.indexOf('\n'), "use an explicit name for the implicit `it` lambda parameter")
    }

    @Test
    fun preservesNestedImplicitShadowing() {
        val source =
            """
            fun nested(values: List<List<Int>>) = values.map { it.map { it + 1 } }
            """.trimIndent() + "\n"
        val expected =
            """
            fun nested(values: List<List<Int>>) = values.map { valueValue -> valueValue.map { value -> value + 1 } }
            """.trimIndent() + "\n"
        val itOffset = source.indexOf("it", source.indexOf('{'))
        assertThat(source)
            .hasLintViolations(
                com.pinterest.ktlint.test
                    .LintViolation(1, itOffset + 1, "use an explicit name for the implicit `it` lambda parameter", true),
                com.pinterest.ktlint.test
                    .LintViolation(1, source.lastIndexOf("it") + 1, "use an explicit name for the implicit `it` lambda parameter", true)
            ).isFormattedAs(expected)
    }

    @Test
    fun preservesMultilineEscapedStringTemplateReferences() {
        val source =
            """
            fun render(items: List<String>) = items.joinToString {
                "\\n${'$'}it"
            }
            """.trimIndent() + "\n"
        val expected =
            """
            fun render(items: List<String>) = items.joinToString {
                value ->
                "\\n${'$'}value"
            }
            """.trimIndent() + "\n"
        val itOffset = source.indexOf("it", source.indexOf('{'))
        assertThat(source)
            .hasLintViolation(2, itOffset - source.indexOf('\n'), "use an explicit name for the implicit `it` lambda parameter")
            .isFormattedAs(expected)
    }

    @Test
    fun preservesRawStringTemplateReferences() {
        val rawQuote = "\"\"\""
        val source =
            """
            fun render(items: List<String>) = items.joinToString { $rawQuote${'$'}it$rawQuote }
            """.trimIndent() + "\n"
        val expected =
            """
            fun render(items: List<String>) = items.joinToString { value -> $rawQuote${'$'}value$rawQuote }
            """.trimIndent() + "\n"
        val itOffset = source.indexOf("it", source.indexOf('{'))
        assertThat(source)
            .hasLintViolation(1, itOffset + 1, "use an explicit name for the implicit `it` lambda parameter")
            .isFormattedAs(expected)
    }

    @Test
    fun doesNotAutocorrectWhenParameterNameWouldCaptureAnExistingReference() {
        val source =
            """
            fun render(items: List<String>, value: String) = items.joinToString { value + it }
            """.trimIndent() + "\n"
        val itOffset = source.lastIndexOf("it")
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(
                1,
                itOffset + 1,
                "use an explicit name for the implicit `it` lambda parameter"
            )
    }

    @Test
    fun acceptsNamedParametersAndNestedTripleQuoteLimitations() {
        val source =
            """
            fun render(items: List<String>) = items.joinToString { value -> value }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }
}
