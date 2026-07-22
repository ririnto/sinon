package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComparisonDirectionTest {
    private val ruleProvider: RuleProvider = RuleProvider { ComparisonDirection() }

    @Test
    fun reportsButNeverAutocorrects() {
        val errors = RuleTestSupport.lintRule(ruleProvider, "fun compare(a: Int, b: Int) = a > b\n")
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun reportsGreaterThanOrEqual() {
        val errors = RuleTestSupport.lintRule(ruleProvider, "fun compare(a: Int, b: Int) = a >= b\n")
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun leavesLessThanOperatorsUnflagged() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "fun compare(a: Int, b: Int) = a < b\n").isEmpty())
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "fun compare(a: Int, b: Int) = a <= b\n").isEmpty())
    }
}
