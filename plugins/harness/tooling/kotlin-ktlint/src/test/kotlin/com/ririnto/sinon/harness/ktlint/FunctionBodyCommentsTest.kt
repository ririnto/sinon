package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class FunctionBodyCommentsTest {
    private val assertThat = assertThatRule { FunctionBodyComments() }

    @Test
    fun rejectsInlineFunctionBodyComments() {
        val source =
            """
            fun work() {
                runTask() // explain the task
            }
            """.trimIndent() + "\n"
        val offset = source.indexOf("//")
        val line = source.take(offset).count { character -> character == '\n' } + 1
        val column = offset - source.lastIndexOf('\n', offset - 1)
        assertThat(source).hasLintViolationWithoutAutoCorrect(line, column, "remove the inline function-body comment")
    }

    @Test
    fun allowsKdocAndToolDirectivesInsideFunctionBodies() {
        val source =
            """
            fun work() {
                /** Local contract. */
                val value = 1
                consume(value)
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }
}
