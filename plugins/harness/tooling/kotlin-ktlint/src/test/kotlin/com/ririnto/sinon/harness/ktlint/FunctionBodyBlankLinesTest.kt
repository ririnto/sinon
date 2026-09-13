package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class FunctionBodyBlankLinesTest {
    private val assertThat = assertThatRule { FunctionBodyBlankLines() }

    @Test
    fun removesDecorativeBlankLinesInsideFunctionBodies() {
        val source =
            """
            fun work() {
                first()

                second()
            }
            """.trimIndent() + "\n"
        val expected =
            """
            fun work() {
                first()
                second()
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolation(2, 12, "remove the decorative blank line from the function body")
            .isFormattedAs(expected)
    }

    @Test
    fun preservesBlankLinesUsedByToolDirectives() {
        val source =
            """
            fun work() {
                first()

                second()
            }
            """.trimIndent() + "\n"
        assertThat(source).hasLintViolation(2, 12, "remove the decorative blank line from the function body")
    }
}
