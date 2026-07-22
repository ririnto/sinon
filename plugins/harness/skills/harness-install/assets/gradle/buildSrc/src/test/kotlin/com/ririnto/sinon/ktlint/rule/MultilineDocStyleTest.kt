package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultilineDocStyleTest {
    private val ruleProvider: RuleProvider = RuleProvider { MultilineDocStyle() }
    private val multilineConfig: String = "[*]\nktlint_multiline_doc_style_mode = multiline\n"

    @Test
    fun autocorrectsSingleLineFunctionDocumentation() {
        val source = "/** hello */\nfun x() {}\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source, multilineConfig)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("/**\n * hello\n */\nfun x() {}\n", RuleTestSupport.formatRule(ruleProvider, source, multilineConfig))
    }

    @Test
    fun trimsPaddedDocumentationContent() {
        assertEquals(
            "/**\n * padded\n */\nfun x() {}\n",
            RuleTestSupport.formatRule(ruleProvider, "/**  padded  */\nfun x() {}\n", multilineConfig)
        )
    }

    @Test
    fun preservesEmptyDocumentationContentAsBlankStarLine() {
        assertEquals("/**\n * \n */\nfun x() {}\n", RuleTestSupport.formatRule(ruleProvider, "/** */\nfun x() {}\n", multilineConfig))
    }

    @Test
    fun leavesAlreadyMultilineDocumentationUnchanged() {
        val source = "/**\n * hi\n */\nfun x() {}\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source, multilineConfig).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source, multilineConfig))
    }

    @Test
    fun leavesDeclarationsWithoutDocumentationUnchanged() {
        val source = "fun x() {}\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source, multilineConfig).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source, multilineConfig))
    }

    @Test
    fun autocorrectsWhenModeIsOn() {
        val source = "/** hello */\nfun x() {}\n"
        val config = "[*]\nktlint_multiline_doc_style_mode = on\n"
        assertEquals(1, RuleTestSupport.lintRule(ruleProvider, source, config).size)
        assertEquals("/**\n * hello\n */\nfun x() {}\n", RuleTestSupport.formatRule(ruleProvider, source, config))
    }

    @Test
    fun preservesAsteriskWithinDocumentationContent() {
        assertEquals(
            "/**\n * a * b\n */\nfun x() {}\n",
            RuleTestSupport.formatRule(ruleProvider, "/** a * b */\nfun x() {}\n", multilineConfig)
        )
    }

    @Test
    fun autocorrectsPropertyDocumentation() {
        assertEquals(
            "/**\n * x\n */\nval v = 1\n",
            RuleTestSupport.formatRule(ruleProvider, "/** x */\nval v = 1\n", multilineConfig)
        )
    }

    @Test
    fun autocorrectsClassDocumentation() {
        assertEquals(
            "/**\n * cls\n */\nclass C\n",
            RuleTestSupport.formatRule(ruleProvider, "/** cls */\nclass C\n", multilineConfig)
        )
    }

    @Test
    fun formattingIsIdempotentAndLintCleanForRepresentativeDocumentation() {
        listOf(
            "/** hello */\nfun x() {}\n",
            "/**  padded  */\nfun x() {}\n",
            "/** a * b */\nfun x() {}\n"
        ).forEach { source ->
            val formatted = RuleTestSupport.formatRule(ruleProvider, source, multilineConfig)
            assertEquals(formatted, RuleTestSupport.formatRule(ruleProvider, formatted, multilineConfig))
            assertTrue(RuleTestSupport.lintRule(ruleProvider, formatted, multilineConfig).isEmpty())
        }
    }

    @Test
    fun isDormantByDefaultAndAcceptsBothEnabledModes() {
        val source = "/** docs */\nfun sample() = 42\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(1, RuleTestSupport.lintRule(ruleProvider, source, multilineConfig).size)
        assertEquals(1, RuleTestSupport.lintRule(ruleProvider, source, "[*]\nktlint_multiline_doc_style_mode = on\n").size)
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source, "[*]\nktlint_multiline_doc_style_mode = off\n").isEmpty())
    }

    @Test
    fun autocorrectsOneLineKDocAndIsIdempotentAcrossAllModes() {
        val config = "[*]\nktlint_multiline_doc_style_mode = multiline\n"
        val source = "/** docs */\nfun sample() = 42\n"
        val formatted = RuleTestSupport.formatRule(ruleProvider, source, config)
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source, config).single().canBeAutoCorrected)
        assertEquals("/**\n * docs\n */\nfun sample() = 42\n", formatted)
        assertEquals(formatted, RuleTestSupport.formatRule(ruleProvider, formatted, config))
        assertFalse(RuleTestSupport.lintRule(ruleProvider, formatted, config).isNotEmpty())
    }
}
