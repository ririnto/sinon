@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class ControlFlowBracesTest {
    private val assertThat = assertThatRule { ControlFlowBraces() }

    @Test
    fun formatsEveryIfBranchWithBraces() {
        val source =
            """
            fun choose(ready: Boolean) {
                if (ready) work()
                else stop()
            }
            """.trimIndent() + "\n"
        val expected =
            """
            fun choose(ready: Boolean) {
                if (ready) {
                    work()
                }
                else {
                    stop()
                }
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolations(
                LintViolation(2, 16, "wrap the `if` branch in `{ ... }`"),
                LintViolation(3, 10, "wrap the `else` branch in `{ ... }`")
            ).isFormattedAs(expected)
    }

    @Test
    fun formatsLoopBodiesWithoutChangingTheLoop() {
        val source =
            """
            fun visit(items: List<Int>, ready: Boolean) {
                for (item in items) use(item)
                while (ready) tick()
                do tick() while (ready)
            }
            """.trimIndent() + "\n"
        val expected =
            """
            fun visit(items: List<Int>, ready: Boolean) {
                for (item in items) {
                    use(item)
                }
                while (ready) {
                    tick()
                }
                do {
                    tick()
                } while (ready)
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolations(
                LintViolation(2, 25, "wrap the `for` body in `{ ... }`"),
                LintViolation(3, 19, "wrap the `while` body in `{ ... }`"),
                LintViolation(4, 8, "wrap the `do-while` body in `{ ... }`")
            ).isFormattedAs(expected)
    }

    @Test
    fun leavesMultilineRawStringBodiesUnchanged() {
        val source = "fun print(ready: Boolean) {\n    if (ready) println(\"\"\"\nvalue\n\"\"\")\n}\n"
        assertThat(source).hasLintViolationWithoutAutoCorrect(2, 16, "wrap the `if` branch in `{ ... }`")
    }
}
