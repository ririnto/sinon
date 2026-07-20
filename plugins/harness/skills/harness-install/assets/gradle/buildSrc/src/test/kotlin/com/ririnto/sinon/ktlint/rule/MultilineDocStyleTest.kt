package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultilineDocStyleTest {
    private val ruleProvider = RuleProvider { MultilineDocStyle() }
    private val multilineConfig = "[*]\nktlint_multiline_doc_style_mode = multiline\n"

    @Test
    fun autocorrectsSingleLineFunctionDocumentation() {
        val source = "/** hello */\nfun x() {}\n"
        val errors = lintRule(ruleProvider, source, multilineConfig)

        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("/**\n * hello\n */\nfun x() {}\n", formatRule(ruleProvider, source, multilineConfig))
    }

    @Test
    fun trimsPaddedDocumentationContent() {
        val source = "/**  padded  */\nfun x() {}\n"

        assertEquals(
            "/**\n * padded\n */\nfun x() {}\n",
            formatRule(ruleProvider, source, multilineConfig)
        )
    }

    @Test
    fun preservesEmptyDocumentationContentAsBlankStarLine() {
        val source = "/** */\nfun x() {}\n"

        assertEquals("/**\n * \n */\nfun x() {}\n", formatRule(ruleProvider, source, multilineConfig))
    }

    @Test
    fun leavesAlreadyMultilineDocumentationUnchanged() {
        val source = "/**\n * hi\n */\nfun x() {}\n"

        assertTrue(lintRule(ruleProvider, source, multilineConfig).isEmpty())
        assertEquals(source, formatRule(ruleProvider, source, multilineConfig))
    }

    @Test
    fun leavesDeclarationsWithoutDocumentationUnchanged() {
        val source = "fun x() {}\n"

        assertTrue(lintRule(ruleProvider, source, multilineConfig).isEmpty())
        assertEquals(source, formatRule(ruleProvider, source, multilineConfig))
    }

    @Test
    fun isDormantWithoutEditorConfig() {
        val source = "/** hello */\nfun x() {}\n"

        assertTrue(lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun autocorrectsWhenModeIsOn() {
        val source = "/** hello */\nfun x() {}\n"
        val config = "[*]\nktlint_multiline_doc_style_mode = on\n"

        assertEquals(1, lintRule(ruleProvider, source, config).size)
        assertEquals("/**\n * hello\n */\nfun x() {}\n", formatRule(ruleProvider, source, config))
    }

    @Test
    fun preservesAsteriskWithinDocumentationContent() {
        val source = "/** a * b */\nfun x() {}\n"

        assertEquals(
            "/**\n * a * b\n */\nfun x() {}\n",
            formatRule(ruleProvider, source, multilineConfig)
        )
    }

    @Test
    fun autocorrectsPropertyDocumentation() {
        val source = "/** x */\nval v = 1\n"

        assertEquals(
            "/**\n * x\n */\nval v = 1\n",
            formatRule(ruleProvider, source, multilineConfig)
        )
    }

    @Test
    fun autocorrectsClassDocumentation() {
        val source = "/** cls */\nclass C\n"

        assertEquals(
            "/**\n * cls\n */\nclass C\n",
            formatRule(ruleProvider, source, multilineConfig)
        )
    }

    @Test
    fun formattingIsIdempotentForRepresentativeDocumentation() {
        val sources = listOf(
            "/** hello */\nfun x() {}\n",
            "/**  padded  */\nfun x() {}\n",
            "/** a * b */\nfun x() {}\n"
        )

        sources.forEach { source ->
            val formatted = formatRule(ruleProvider, source, multilineConfig)
            assertEquals(formatted, formatRule(ruleProvider, formatted, multilineConfig))
        }
    }

    @Test
    fun formattedRepresentativeDocumentationHasNoRemainingLintErrors() {
        val sources = listOf(
            "/** hello */\nfun x() {}\n",
            "/**  padded  */\nfun x() {}\n",
            "/** a * b */\nfun x() {}\n"
        )

        sources.forEach { source ->
            val formatted = formatRule(ruleProvider, source, multilineConfig)
            assertTrue(lintRule(ruleProvider, formatted, multilineConfig).isEmpty())
        }
    }

    @Test
    fun isDormantByDefaultAndAcceptsBothEnabledModes() {
        val source = "/** docs */\nfun sample() = 42\n"
        val onConfig = "[*]\nktlint_multiline_doc_style_mode = on\n"
        val offConfig = "[*]\nktlint_multiline_doc_style_mode = off\n"
        assertTrue(lintRule(ruleProvider, source).isEmpty())
        assertEquals(1, lintRule(ruleProvider, source, multilineConfig).size)
        assertEquals(1, lintRule(ruleProvider, source, onConfig).size)
        assertTrue(lintRule(ruleProvider, source, offConfig).isEmpty())
    }

    @Test
    fun autocorrectsOneLineKDocAndIsIdempotentAcrossAllModes() {
        val config = "[*]\nktlint_multiline_doc_style_mode = multiline\n"
        val source = "/** docs */\nfun sample() = 42\n"
        val formatted = formatRule(ruleProvider, source, config)
        assertTrue(lintRule(ruleProvider, source, config).single().canBeAutoCorrected)
        assertEquals("/**\n * docs\n */\nfun sample() = 42\n", formatted)
        assertEquals(formatted, formatRule(ruleProvider, formatted, config))
        assertFalse(lintRule(ruleProvider, formatted, config).isNotEmpty())
    }
}
