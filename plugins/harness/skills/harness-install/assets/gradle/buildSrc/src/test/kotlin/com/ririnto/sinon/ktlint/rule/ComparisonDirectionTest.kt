package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ComparisonDirectionTest {
    private val ruleProvider = RuleProvider { ComparisonDirection() }

    @Test
    fun reportsButNeverAutocorrects() {
        val source = "fun compare(a: Int, b: Int) = a > b\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }
}
