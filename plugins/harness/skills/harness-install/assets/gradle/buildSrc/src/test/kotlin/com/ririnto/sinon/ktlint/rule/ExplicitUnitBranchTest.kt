package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExplicitUnitBranchTest {
    private val ruleProvider: RuleProvider = RuleProvider { ExplicitUnitBranch() }

    @Test
    fun flagsOnlyExplicitUnitResultsInMixedWhenBranches() {
        val source = """
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

        val errors = RuleTestSupport.lintRule(ruleProvider, source)

        assertExplicitUnitDiagnostics(errors, expectedCount = 6)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun ignoresNonExplicitUnitBranchResults() {
        val cases = listOf(
            "emptyBlock" to "0 -> {}",
            "call" to "0 -> value.toString()",
            "label" to "0 -> branch@ Unit",
            "return" to "0 -> return",
            "arbitraryQualifiedUnit" to "0 -> other.Unit",
            "nonTerminalUnit" to "0 -> { Unit; value }"
        )

        cases.forEach { (caseName, branch) ->
            val source = """
                fun sample(value: Int) {
                    when (value) {
                        $branch
                        else -> value
                    }
                }
            """.trimIndent() + "\n"

            assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty(), caseName)
        }
    }

    @Test
    fun flagsUnitResultInIfWithoutElse() {
        val source = """
            fun sample(condition: Boolean) {
                if (condition) Unit
            }
        """.trimIndent() + "\n"

        assertExplicitUnitDiagnostics(RuleTestSupport.lintRule(ruleProvider, source), expectedCount = 1)
    }

    @Test
    fun flagsBothBranchesInSimpleIfElse() {
        val source = """
            fun sample(condition: Boolean) {
                if (condition) Unit else Unit
            }
        """.trimIndent() + "\n"

        assertExplicitUnitDiagnostics(RuleTestSupport.lintRule(ruleProvider, source), expectedCount = 2)
    }

    @Test
    fun flagsEachUnitResultOnceInElseIfChain() {
        val source = """
            fun sample(first: Boolean, second: Boolean) {
                if (first) Unit else if (second) Unit else Unit
            }
        """.trimIndent() + "\n"

        val errors = RuleTestSupport.lintRule(ruleProvider, source)

        assertExplicitUnitDiagnostics(errors, expectedCount = 3)
        assertEquals(errors.size, errors.map { error -> error.line to error.col }.distinct().size)
    }

    @Test
    fun traversesNestedControlFlowWithoutFlaggingOuterBranches() {
        val source = """
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

        assertExplicitUnitDiagnostics(RuleTestSupport.lintRule(ruleProvider, source), expectedCount = 2)
    }

    private fun assertExplicitUnitDiagnostics(errors: List<LintError>, expectedCount: Int) {
        assertEquals(expectedCount, errors.size)
        errors.forEach { error ->
            assertEquals(RuleId("code:explicit-unit-branch"), error.ruleId)
            assertEquals("explicit Unit branch result is forbidden", error.detail)
            assertFalse(error.canBeAutoCorrected)
        }
    }
}
