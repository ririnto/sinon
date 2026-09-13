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
            fun render(items: List<String>) = items.joinToString { value -> "\\n${'$'}it" }
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
    fun acceptsNamedParametersAndNestedTripleQuoteLimitations() {
        val source =
            """
            fun render(items: List<String>) = items.joinToString { value -> value }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }
}
