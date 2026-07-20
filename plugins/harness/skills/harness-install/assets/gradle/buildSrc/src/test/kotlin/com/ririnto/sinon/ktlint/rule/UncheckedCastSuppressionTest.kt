package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UncheckedCastSuppressionTest {
    private val ruleProvider = RuleProvider { UncheckedCastSuppression() }

    @Test
    fun staleSuppressOnFunctionWithoutCast_autocorrectsByRemovingAnnotation() {
        val source = """
            @Suppress("UNCHECKED_CAST")
            fun sample(): Int = 42
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("fun sample(): Int = 42\n", formatRule(ruleProvider, source))
    }

    @Test
    fun staleSuppressOnPropertyInitializer_autocorrects() {
        val source = """
            @Suppress("UNCHECKED_CAST")
            val sample: Int = 42
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("val sample: Int = 42\n", formatRule(ruleProvider, source))
    }

    @Test
    fun staleSuppressOnClassWithNoCasts_autocorrects() {
        val source = """
            @Suppress("UNCHECKED_CAST")
            class Sample {
                val value: Int = 42
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "class Sample {\n    val value: Int = 42\n}\n",
            formatRule(ruleProvider, source)
        )
    }

    @Test
    fun staleFileLevelSuppress_autocorrectsAndRemovesFileAnnotationLine() {
        val source = """
            @file:Suppress("UNCHECKED_CAST")

            package com.example

            fun sample(): Int = 42
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        val formatted = formatRule(ruleProvider, source)
        assertFalse(formatted.contains("Suppress"))
        assertTrue(formatted.contains("fun sample(): Int = 42"))
    }

    @Test
    fun staleSuppressAmongOtherAnnotations_removesOnlyTheSuppressEntry() {
        val source = """
            @Deprecated("old")
            @Suppress("UNCHECKED_CAST")
            fun sample(): Int = 42
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        val formatted = formatRule(ruleProvider, source)
        assertTrue(formatted.contains("@Deprecated(\"old\")"))
        assertFalse(formatted.contains("Suppress"))
    }

    @Test
    fun safeShapeFormatIsIdempotent() {
        val source = """
            @Suppress("UNCHECKED_CAST")
            fun sample(): Int = 42
        """.trimIndent() + "\n"
        val once = formatRule(ruleProvider, source)
        val twice = formatRule(ruleProvider, once)
        assertEquals(once, twice)
        assertTrue(lintRule(ruleProvider, once).isEmpty())
    }

    @Test
    fun liveSuppressOnFunctionWithAsCast_remainsUncorrected() {
        val source = """
            @Suppress("UNCHECKED_CAST")
            fun sample(value: Any): String = value as String
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun liveSuppressOnFunctionWithAsSafeCast_remainsUncorrected() {
        val source = """
            @Suppress("UNCHECKED_CAST")
            fun sample(value: Any): String? = value as? String
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun multiArgSuppressWithForbiddenToken_remainsUncorrectedEvenWhenStale() {
        val source = """
            @Suppress("UNCHECKED_CAST", "DEPRECATION")
            fun sample(): Int = 42
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
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
        assertTrue(lintRule(ruleProvider, source, config).isEmpty())
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
        val errors = lintRule(ruleProvider, source, config)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("fun sample(): Int = 42\n", formatRule(ruleProvider, source, config))
    }

    @Test
    fun nonSuppressAnnotationIsIgnored() {
        val source = """
            @Deprecated("use newSample instead")
            fun sample(): Int = 42
        """.trimIndent() + "\n"
        assertTrue(lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun fullyQualifiedSuppressAnnotationIsDetected() {
        val source = """
            @kotlin.Suppress("UNCHECKED_CAST")
            fun sample(): Int = 42
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("fun sample(): Int = 42\n", formatRule(ruleProvider, source))
    }
}
