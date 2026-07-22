package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NonNullAssertionTest {
    private val ruleProvider: RuleProvider = RuleProvider { NonNullAssertion() }

    @Test
    fun autocorrectsRedundantNonNullAssertionOnRequireNotNull() {
        val source = """
            fun sample(value: String?): String = requireNotNull(value)!!
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "fun sample(value: String?): String = requireNotNull(value)\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun autocorrectsRedundantNonNullAssertionOnCheckNotNull() {
        val source = """
            fun sample(value: String?): String = checkNotNull(value)!!
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "fun sample(value: String?): String = checkNotNull(value)\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun autocorrectsRedundantGuardOnComplexArgumentExpression() {
        val source = """
            fun sample(map: Map<String, String>): String = requireNotNull(map["key"])!!
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "fun sample(map: Map<String, String>): String = requireNotNull(map[\"key\"])\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun autocorrectsBareVariableAssertionToRequireNotNull() {
        val source = """
            fun sample(value: String?): String = value!!
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "fun sample(value: String?): String = requireNotNull(value)\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun autocorrectsMemberAccessOnNullableReceiver() {
        val source = """
            fun sample(value: String?): Int = value!!.length
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "fun sample(value: String?): Int = requireNotNull(value).length\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun autocorrectsMemberCallOnNullableReceiver() {
        assertEquals(
            "fun sample(value: List<Int>?): Int = requireNotNull(value).size\n",
            RuleTestSupport.formatRule(
                ruleProvider,
                """
                fun sample(value: List<Int>?): Int = value!!.size
                """.trimIndent() + "\n"
            )
        )
    }

    @Test
    fun autocorrectsArbitraryFunctionCallResult() {
        assertEquals(
            "fun sample(): String = requireNotNull(compute())\n",
            RuleTestSupport.formatRule(
                ruleProvider,
                """
                fun sample(): String = compute()!!
                """.trimIndent() + "\n"
            )
        )
    }

    @Test
    fun autocorrectsPropertyAccess() {
        assertEquals(
            "class Service(val value: String?)\nfun sample(service: Service): String = requireNotNull(service.value)\n",
            RuleTestSupport.formatRule(
                ruleProvider,
                """
                class Service(val value: String?)
                fun sample(service: Service): String = service.value!!
                """.trimIndent() + "\n"
            )
        )
    }

    @Test
    fun autocorrectsIndexAccess() {
        assertEquals(
            "fun sample(map: Map<String, String>): String = requireNotNull(map[\"key\"])\n",
            RuleTestSupport.formatRule(
                ruleProvider,
                """
                fun sample(map: Map<String, String>): String = map["key"]!!
                """.trimIndent() + "\n"
            )
        )
    }

    @Test
    fun autocorrectsChainWithMemberAccessOnGuard() {
        val source = """
            fun sample(value: String?): Int = requireNotNull(value)!!.length
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "fun sample(value: String?): Int = requireNotNull(value).length\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun autocorrectsMemberFunctionWithSimilarName() {
        assertEquals(
            "class Service {\n    fun requireNonNull(): String = \"value\"\n}\nfun sample(service: Service): String = requireNotNull(service.requireNonNull())\n",
            RuleTestSupport.formatRule(
                ruleProvider,
                """
                class Service {
                    fun requireNonNull(): String = "value"
                }
                fun sample(service: Service): String = service.requireNonNull()!!
                """.trimIndent() + "\n"
            )
        )
    }

    @Test
    fun disablesAutocorrectWhenUserDefinedGuardShadowsStdlib() {
        val source = """
            fun requireNotNull(value: Any?): String = value.toString()
            fun sample(value: String?): String = value!!
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun disablesAutocorrectWhenStarImportCouldProvideGuard() {
        val source = """
            import custom.guards.*

            fun sample(value: String?): String = value!!
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun keepsAutocorrectWhenOnlyStdlibGuardIsImportedExplicitly() {
        val source = """
            import kotlin.requireNotNull

            fun sample(value: String?): String = value!!
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "import kotlin.requireNotNull\n\nfun sample(value: String?): String = requireNotNull(value)\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun disablesAutocorrectWhenCustomGuardIsAliasedToStdlibName() {
        val source = """
            import custom.requireNotNull as requireNotNull

            fun sample(value: String?): String = value!!
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun unaliasedCustomGuardImportDisablesAutocorrect() {
        val source = """
            import com.example.requireNotNull

            fun sample(value: Any?): Int = value!!
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
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
            val once = RuleTestSupport.formatRule(ruleProvider, source)
            assertEquals(once, RuleTestSupport.formatRule(ruleProvider, once), source)
            assertTrue(RuleTestSupport.lintRule(ruleProvider, once).isEmpty(), source)
        }
    }
}
