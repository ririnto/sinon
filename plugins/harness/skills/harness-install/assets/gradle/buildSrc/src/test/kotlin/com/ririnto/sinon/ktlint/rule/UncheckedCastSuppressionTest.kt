package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UncheckedCastSuppressionTest {
    private val ruleProvider: RuleProvider = RuleProvider { UncheckedCastSuppression() }

    @Test
    fun staleSuppressOnFunctionWithoutCastAutocorrectsByRemovingAnnotation() {
        val source = """
            @Suppress("UNCHECKED_CAST")
            fun sample(): Int = 42
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("fun sample(): Int = 42\n", RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun staleSuppressOnPropertyInitializerAutocorrects() {
        val source = """
            @Suppress("UNCHECKED_CAST")
            val sample: Int = 42
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("val sample: Int = 42\n", RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun staleSuppressOnClassWithNoCastsAutocorrects() {
        val source = """
            @Suppress("UNCHECKED_CAST")
            class Sample {
                val value: Int = 42
            }
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("class Sample {\n    val value: Int = 42\n}\n", RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun staleFileLevelSuppressAutocorrectsAndRemovesFileAnnotationLine() {
        val source = """
            @file:Suppress("UNCHECKED_CAST")

            package com.example

            fun sample(): Int = 42
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        val formatted = RuleTestSupport.formatRule(ruleProvider, source)
        assertFalse(formatted.contains("Suppress"))
        assertTrue(formatted.contains("fun sample(): Int = 42"))
    }

    @Test
    fun staleSuppressAmongOtherAnnotationsRemovesOnlyTheSuppressEntry() {
        val source = """
            @Deprecated("old")
            @Suppress("UNCHECKED_CAST")
            fun sample(): Int = 42
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        val formatted = RuleTestSupport.formatRule(ruleProvider, source)
        assertTrue(formatted.contains("@Deprecated(\"old\")"))
        assertFalse(formatted.contains("Suppress"))
    }

    @Test
    fun liveSuppressOnFunctionWithAsCastRemainsUncorrected() {
        val source = """
            @Suppress("UNCHECKED_CAST")
            fun sample(value: Any): String = value as String
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun liveSuppressOnFunctionWithAsSafeCastRemainsUncorrected() {
        val source = """
            @Suppress("UNCHECKED_CAST")
            fun sample(value: Any): String? = value as? String
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun multiArgSuppressWithForbiddenTokenRemainsUncorrectedEvenWhenStale() {
        val source = """
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            fun sample(): Int = 42
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun allowedTokenFilterSuppressesDetection() {
        val config = """
            root = true

            [*]
            ktlint_unchecked_cast_suppression_allowed = UNCHECKED_CAST
        """.trimIndent() + "\n"
        val source = """
            @Suppress("UNCHECKED_CAST")
            fun sample(): Int = 42
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source, config).isEmpty())
    }

    @Test
    fun customForbiddenTokenDetectedAndAutocorrectsWhenStale() {
        val config = """
            root = true

            [*]
            ktlint_unchecked_cast_suppression_forbidden = USELESS_CAST
        """.trimIndent() + "\n"
        val source = """
            @Suppress("USELESS_CAST")
            fun sample(): Int = 42
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source, config)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("fun sample(): Int = 42\n", RuleTestSupport.formatRule(ruleProvider, source, config))
    }

    @Test
    fun nonSuppressAnnotationIsIgnored() {
        val source = """
            @Deprecated("use newSample instead")
            fun sample(): Int = 42
        """.trimIndent() + "\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun fullyQualifiedSuppressAnnotationIsDetected() {
        val source = """
            @kotlin.Suppress("UNCHECKED_CAST")
            fun sample(): Int = 42
        """.trimIndent() + "\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("fun sample(): Int = 42\n", RuleTestSupport.formatRule(ruleProvider, source))
    }
}
