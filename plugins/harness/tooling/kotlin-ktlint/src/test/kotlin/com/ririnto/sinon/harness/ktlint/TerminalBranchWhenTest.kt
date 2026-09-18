@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class TerminalBranchWhenTest {
    private val assertThat = assertThatRule { TerminalBranchWhen() }

    @Test
    fun reportsOnlyOutermostIfElseChainWithFinalElse() {
        val source =
            """
            fun sample(first: Boolean, second: Boolean) {
                if (first) {
                    work()
                } else if (second) {
                    continueWork()
                } else {
                    finish()
                }
                if (first) work()
            }
            """.trimIndent() + "\n"
        assertThat(source).hasLintViolationWithoutAutoCorrect(2, 5, "if/else chain; use when instead")
    }

    @Test
    fun ignoresIfWithoutFinalElse() {
        val source =
            """
            fun sample(first: Boolean, second: Boolean) {
                if (first) work()
                if (first) work() else if (second) continueWork()
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }
}
