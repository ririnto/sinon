@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class ExplicitUnitBranchTest {
    private val assertThat = assertThatRule { ExplicitUnitBranch() }

    @Test
    fun flagsOnlyExplicitUnitResultsInMixedWhenBranches() {
        val source =
            """
            fun sample(value: Int) {
                when (value) {
                    0 -> Unit
                    1 -> value
                    2 -> kotlin.Unit
                    3 -> value.toString()
                    4 -> kotlin . Unit
                    5 -> (Unit)
                    6 -> {
                        val ignored = value
                        Unit
                    }
                    else -> Unit
                }
            }
            """.trimIndent() + "\n"
        assertThat(source).hasLintViolationsWithoutAutoCorrect(
            LintViolation(3, 14, "explicit Unit branch result is forbidden"),
            LintViolation(5, 14, "explicit Unit branch result is forbidden"),
            LintViolation(7, 14, "explicit Unit branch result is forbidden"),
            LintViolation(8, 14, "explicit Unit branch result is forbidden"),
            LintViolation(9, 14, "explicit Unit branch result is forbidden"),
            LintViolation(13, 17, "explicit Unit branch result is forbidden")
        )
    }

    @Test
    fun ignoresNonExplicitUnitBranchResults() {
        listOf(
            "0 -> {}",
            "0 -> value.toString()",
            "0 -> branch@ Unit",
            "0 -> return",
            "0 -> other.Unit",
            "0 -> { Unit; value }"
        ).forEach { branch ->
            val source =
                """
                fun sample(value: Int) {
                    when (value) {
                        $branch
                        else -> value
                    }
                }
                """.trimIndent() + "\n"
            assertThat(source).hasNoLintViolations()
        }
    }

    @Test
    fun flagsUnitResultInIfWithoutElse() {
        val source =
            """
            fun sample(condition: Boolean) {
                if (condition) Unit
            }
            """.trimIndent() + "\n"
        assertThat(source).hasLintViolationWithoutAutoCorrect(2, 20, "explicit Unit branch result is forbidden")
    }

    @Test
    fun flagsBothBranchesInSimpleIfElse() {
        val source =
            """
            fun sample(condition: Boolean) {
                if (condition) Unit else Unit
            }
            """.trimIndent() + "\n"
        assertThat(source).hasLintViolationsWithoutAutoCorrect(
            LintViolation(2, 20, "explicit Unit branch result is forbidden"),
            LintViolation(2, 30, "explicit Unit branch result is forbidden")
        )
    }

    @Test
    fun flagsEachUnitResultOnceInElseIfChain() {
        val source =
            """
            fun sample(first: Boolean, second: Boolean) {
                if (first) Unit else if (second) Unit else Unit
            }
            """.trimIndent() + "\n"
        assertThat(source).hasLintViolationsWithoutAutoCorrect(
            LintViolation(2, 16, "explicit Unit branch result is forbidden"),
            LintViolation(2, 38, "explicit Unit branch result is forbidden"),
            LintViolation(2, 48, "explicit Unit branch result is forbidden")
        )
    }

    @Test
    fun traversesNestedControlFlowWithoutFlaggingOuterBranches() {
        val source =
            """
            fun sample(value: Int, condition: Boolean) {
                when (value) {
                    0 -> if (condition) Unit else value
                    1 -> when (value) {
                        0 -> Unit
                        else -> value
                    }
                    else -> value
                }
            }
            """.trimIndent() + "\n"
        assertThat(source).hasLintViolationsWithoutAutoCorrect(
            LintViolation(3, 29, "explicit Unit branch result is forbidden"),
            LintViolation(5, 18, "explicit Unit branch result is forbidden")
        )
    }
}
