@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class UnstructuredLoggingTest {
    private val assertThat = assertThatRule { UnstructuredLogging() }

    @Test
    fun printlnIsFlagged() {
        assertThat("fun log() = println(\"message\")\n")
            .hasLintViolationWithoutAutoCorrect(1, 13, "unstructured logging `println`; use structured logger")
    }

    @Test
    fun qualifiedPrintlnIsFlagged() {
        assertThat("fun log() = kotlin.io.println(\"message\")\n")
            .hasLintViolationWithoutAutoCorrect(1, 23, "unstructured logging `kotlin.io.println`; use structured logger")
    }

    @Test
    fun multiplePrintlnCallsAreAllFlagged() {
        val source =
            """
            fun log() {
                println("one")
                kotlin.io.println("two")
            }
            """.trimIndent() + "\n"
        assertThat(source).hasLintViolationsWithoutAutoCorrect(
            LintViolation(2, 5, "unstructured logging `println`; use structured logger"),
            LintViolation(3, 15, "unstructured logging `kotlin.io.println`; use structured logger")
        )
    }

    @Test
    fun receiverPrintlnIsSafe() {
        assertThat("class Logger { fun println(msg: String) {} }\nfun log(l: Logger) = l.println(\"message\")\n")
            .hasNoLintViolations()
    }

    @Test
    fun nonKotlinCalleeIsSafe() {
        assertThat("fun log(error: Throwable) = error.printStackTrace()\n").hasNoLintViolations()
        assertThat("fun log() = print(\"message\")\n").hasNoLintViolations()
    }

    @Test
    fun safeAccessAndNotNullAssertionReceiversAreSafe() {
        val source =
            """
            class Logger {
                fun println(message: String) {}
            }

            fun log(foo: Logger?) {
                foo?.println("msg")
                foo!!.println("msg")
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }
}
