package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OtherCustomRulesTest {
    @Test
    fun decorativeFunctionBodyBlankLinesRemainAutocorrectableAndIdempotent() {
        val ruleProvider = RuleProvider { DecorativeFunctionBodyBlankLinesKtlintRule() }
        val source = """
            fun sample() {

                work()
            }
        """.trimIndent() + "\n"
        val errors = lintRule(ruleProvider, source)
        val formatted = formatRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals("fun sample() {\n    work()\n}\n", formatted)
        assertEquals(formatted, formatRule(ruleProvider, formatted))
    }

    @Test
    fun multilineDocStyleIsOffByDefaultAndAcceptsBothEnabledModes() {
        val ruleProvider = RuleProvider { MultilineDocStyleKtlintRule() }
        val source = "/** docs */\nfun sample() = 42\n"
        val multilineConfig = "root = true\n\n[*]\nktlint_multiline_doc_style_mode = multiline\n"
        val onConfig = "root = true\n\n[*]\nktlint_multiline_doc_style_mode = on\n"
        assertTrue(lintRule(ruleProvider, source).isEmpty())
        assertEquals(1, lintRule(ruleProvider, source, multilineConfig).size)
        assertEquals(1, lintRule(ruleProvider, source, onConfig).size)
        assertTrue(lintRule(ruleProvider, source, "root = true\n\n[*]\nktlint_multiline_doc_style_mode = off\n").isEmpty())
    }

    @Test
    fun multilineDocStyleAutocorrectsOneLineKDocAndIsIdempotent() {
        val ruleProvider = RuleProvider { MultilineDocStyleKtlintRule() }
        val config = "root = true\n\n[*]\nktlint_multiline_doc_style_mode = multiline\n"
        val source = "/** docs */\nfun sample() = 42\n"
        val formatted = formatRule(ruleProvider, source, config)
        assertTrue(lintRule(ruleProvider, source, config).single().canBeAutoCorrected)
        assertEquals("/**\n * docs\n */\nfun sample() = 42\n", formatted)
        assertEquals(formatted, formatRule(ruleProvider, formatted, config))
        assertFalse(lintRule(ruleProvider, formatted, config).isNotEmpty())
    }

    @Test
    fun comparisonDirectionReportsButNeverAutocorrects() {
        val ruleProvider = RuleProvider { ComparisonDirectionKtlintRule() }
        val source = "fun compare(a: Int, b: Int) = a > b\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }
}
