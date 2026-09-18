@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class ComparisonDirectionTest {
    private val assertThat = assertThatRule { ComparisonDirection() }

    @Test
    fun reportsButNeverAutocorrects() {
        assertThat("fun compare(a: Int, b: Int) = a > b\n")
            .hasLintViolationWithoutAutoCorrect(1, 33, "avoid `>` in comparisons; prefer `<` with operands swapped")
    }

    @Test
    fun reportsGreaterThanOrEqual() {
        assertThat("fun compare(a: Int, b: Int) = a >= b\n")
            .hasLintViolationWithoutAutoCorrect(1, 33, "avoid `>=` in comparisons; prefer `<=` with operands swapped")
    }

    @Test
    fun leavesLessThanOperatorsUnflagged() {
        assertThat("fun compare(a: Int, b: Int) = a < b\n").hasNoLintViolations()
        assertThat("fun compare(a: Int, b: Int) = a <= b\n").hasNoLintViolations()
    }
}
