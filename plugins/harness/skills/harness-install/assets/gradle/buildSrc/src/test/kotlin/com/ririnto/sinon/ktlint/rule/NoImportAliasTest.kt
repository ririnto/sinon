package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoImportAliasTest {
    private val ruleProvider: RuleProvider = RuleProvider { NoImportAlias() }

    @Test
    fun aliasMatchingSimpleNameIsFlagged() {
        val errors = RuleTestSupport.lintRule(ruleProvider, "import a.Foo as Foo\n")
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun aliasDifferentFromSimpleNameIsAllowed() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "import a.Foo as Bar\n").isEmpty())
    }

    @Test
    fun importWithoutAliasIsNotFlagged() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "import a.Foo\n").isEmpty())
    }
}
