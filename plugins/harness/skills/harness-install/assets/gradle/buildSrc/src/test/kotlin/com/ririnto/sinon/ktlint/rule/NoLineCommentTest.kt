package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoLineCommentTest {
    private val ruleProvider: RuleProvider = RuleProvider { NoLineComment() }

    @Test
    fun standaloneLineCommentIsFlagged() {
        val errors = RuleTestSupport.lintRule(ruleProvider, "fun foo() {\n    // comment\n}\n")
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun trailingLineCommentIsFlagged() {
        val errors = RuleTestSupport.lintRule(ruleProvider, "fun foo() {\n    val x = 1 // comment\n}\n")
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun blockCommentIsFlagged() {
        val errors = RuleTestSupport.lintRule(ruleProvider, "fun foo() {\n    /* block */\n}\n")
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun multilineBlockCommentIsFlagged() {
        val errors = RuleTestSupport.lintRule(ruleProvider, "fun foo() {\n    /*\n     * block\n     */\n}\n")
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun mixedLineAndBlockCommentsAreAllFlagged() {
        val errors = RuleTestSupport.lintRule(ruleProvider, "fun foo() {\n    // one\n    val x = 1 // two\n    /* three */ val y = 2\n}\n")
        assertEquals(3, errors.size)
        assertTrue(errors.all { error -> !error.canBeAutoCorrected })
    }

    @Test
    fun kdocCommentIsNotFlagged() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "/** docs */\nfun foo()\n").isEmpty())
    }

    @Test
    fun commentAtTopOfFileIsFlagged() {
        val errors = RuleTestSupport.lintRule(ruleProvider, "// file-level comment\nfun foo()\n")
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }
}
