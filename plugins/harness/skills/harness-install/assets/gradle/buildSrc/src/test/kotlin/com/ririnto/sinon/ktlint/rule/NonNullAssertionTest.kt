package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NonNullAssertionTest {
    private val ruleProvider = RuleProvider { NonNullAssertion() }

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
    fun autocorrectsRedundantGuardOnComplexArgumentExpression() {
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
    fun autocorrectsBareVariableAssertionToRequireNotNull() {
        val source = """
            fun sample(value: String?): String = value!!
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
    fun autocorrectsMemberAccessOnNullableReceiver() {
        val source = """
            fun sample(value: String?): Int = value!!.length
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "fun sample(value: String?): Int = requireNotNull(value).length\n",
            formatRule(ruleProvider, source)
        )
    }

    @Test
    fun autocorrectsMemberCallOnNullableReceiver() {
        val source = """
            fun sample(value: List<Int>?): Int = value!!.size
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
        assertEquals(
            "fun sample(value: List<Int>?): Int = requireNotNull(value).size\n",
            formatted
        )
    }

    @Test
    fun autocorrectsArbitraryFunctionCallResult() {
        val source = """
            fun sample(): String = compute()!!
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
        assertEquals(
            "fun sample(): String = requireNotNull(compute())\n",
            formatted
        )
    }

    @Test
    fun autocorrectsPropertyAccess() {
        val source = """
            class Service(val value: String?)
            fun sample(service: Service): String = service.value!!
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
        assertEquals(
            "class Service(val value: String?)\nfun sample(service: Service): String = requireNotNull(service.value)\n",
            formatted
        )
    }

    @Test
    fun autocorrectsIndexAccess() {
        val source = """
            fun sample(map: Map<String, String>): String = map["key"]!!
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
        assertEquals(
            "fun sample(map: Map<String, String>): String = requireNotNull(map[\"key\"])\n",
            formatted
        )
    }

    @Test
    fun autocorrectsChainWithMemberAccessOnGuard() {
        val source = """
            fun sample(value: String?): Int = requireNotNull(value)!!.length
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "fun sample(value: String?): Int = requireNotNull(value).length\n",
            formatRule(ruleProvider, source)
        )
    }

    @Test
    fun autocorrectsMemberFunctionWithSimilarName() {
        val source = """
            class Service {
                fun requireNonNull(): String = "value"
            }
            fun sample(service: Service): String = service.requireNonNull()!!
        """.trimIndent() + "\n"
        val formatted = formatRule(ruleProvider, source)
        assertEquals(
            "class Service {\n    fun requireNonNull(): String = \"value\"\n}\nfun sample(service: Service): String = requireNotNull(service.requireNonNull())\n",
            formatted
        )
    }

    @Test
    fun autocorrectIsIdempotentAcrossShapes() {
        val sources = listOf(
            "fun sample(value: String?): String = requireNotNull(value)!!\n",
            "fun sample(value: String?): String = value!!\n",
            "fun sample(value: String?): Int = value!!.length\n",
            "fun sample(map: Map<String, String>): String = map[\"key\"]!!\n"
        )
        sources.forEach { source ->
            val once = formatRule(ruleProvider, source)
            val twice = formatRule(ruleProvider, once)
            assertEquals(once, twice, source)
            assertTrue(lintRule(ruleProvider, once).isEmpty(), source)
        }
    }
}
