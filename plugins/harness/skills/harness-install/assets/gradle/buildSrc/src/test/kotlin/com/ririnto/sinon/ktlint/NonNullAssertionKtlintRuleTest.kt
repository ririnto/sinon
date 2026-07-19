package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NonNullAssertionKtlintRuleTest {
    private val ruleProvider = RuleProvider { NonNullAssertionKtlintRule() }

    @Test
    fun autocorrectsRedundantNonNullAssertionOnRequireNotNull() {
        val source = """
            fun sample(value: String?): String = requireNotNull(value)!!
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "fun sample(value: String?): String = requireNotNull(value)\n",
            formatRule(ruleProvider, source)
        )
    }

    @Test
    fun autocorrectsRedundantNonNullAssertionOnCheckNotNull() {
        val source = """
            fun sample(value: String?): String = checkNotNull(value)!!
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "fun sample(value: String?): String = checkNotNull(value)\n",
            formatRule(ruleProvider, source)
        )
    }

    @Test
    fun autocorrectIsIdempotentOnFormattedOutput() {
        val source = "fun sample(value: String?): String = requireNotNull(value)!!\n"
        val once = formatRule(ruleProvider, source)
        val twice = formatRule(ruleProvider, once)
        assertEquals(once, twice)
        assertTrue(lintRule(ruleProvider, once).isEmpty())
    }

    @Test
    fun autocorrectsRequireNotNullWithComplexArgumentExpression() {
        val source = """
            fun sample(map: Map<String, String>): String = requireNotNull(map["key"])!!
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "fun sample(map: Map<String, String>): String = requireNotNull(map[\"key\"])\n",
            formatRule(ruleProvider, source)
        )
    }

    @Test
    fun leavesLintOnlyForNullableVariableAssertion() {
        val source = """
            fun sample(value: String?): String = value!!
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyForArbitraryFunctionCall() {
        val source = """
            fun sample(): String = compute()!!
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyForPropertyAccess() {
        val source = """
            class Service(val value: String?)
            fun sample(service: Service): String = service.value!!
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun leavesLintOnlyForMemberFunctionWithSimilarName() {
        val source = """
            class Service {
                fun requireNonNull(): String = "value"
            }
            fun sample(service: Service): String = service.requireNonNull()!!
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }
}
