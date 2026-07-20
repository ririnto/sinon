package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoImportAliasTest {
    private val ruleProvider = RuleProvider { NoImportAlias() }

    @Test
    fun aliasMatchingSimpleNameIsFlagged() {
        val source = "import a.Foo as Foo\n"
        val errors = lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun aliasDifferentFromSimpleNameIsAllowed() {
        val source = "import a.Foo as Bar\n"
        assertTrue(lintRule(ruleProvider, source).isEmpty())
    }

    @Test
    fun importWithoutAliasIsNotFlagged() {
        val source = "import a.Foo\n"
        assertTrue(lintRule(ruleProvider, source).isEmpty())
    }
}
