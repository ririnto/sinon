package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicDeclarationDocCommentTest {
    private val ruleProvider: RuleProvider = RuleProvider { PublicDeclarationDocComment() }
    private val enabledConfig: String = "[*]\nktlint_public_declaration_doc_comment_mode = on\n"
    private val publicConfig: String = "[*]\nktlint_public_declaration_doc_comment_mode = public\n"
    private val disabledConfig: String = "[*]\nktlint_public_declaration_doc_comment_mode = off\n"

    @Test
    fun isDisabledByDefault() {
        val source = "class Foo\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun flagsMissingClassDocumentationWhenModeIsOn() {
        val source = "class Foo\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source, enabledConfig)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun flagsMissingClassDocumentationWhenModeIsPublic() {
        val errors = RuleTestSupport.lintRule(ruleProvider, "class Foo\n", publicConfig)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun remainsDisabledWhenModeIsOff() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "class Foo\n", disabledConfig).isEmpty())
    }

    @Test
    fun acceptsDocumentedClass() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "/** Doc */\nclass Foo\n", enabledConfig).isEmpty())
    }

    @Test
    fun flagsUndocumentedTopLevelFunction() {
        assertEquals(1, RuleTestSupport.lintRule(ruleProvider, "fun foo() {}\n", enabledConfig).size)
    }

    @Test
    fun acceptsDocumentedTopLevelFunction() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "/** Doc */\nfun foo() {}\n", enabledConfig).isEmpty())
    }

    @Test
    fun excludesOverrideFunction() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "/** Base */\nopen class Base { /** Foo */ open fun foo() {} }\n/** Derived */\nclass Derived : Base() { override fun foo() {} }\n", enabledConfig).isEmpty())
    }

    @Test
    fun excludesLocalFunction() {
        assertEquals(1, RuleTestSupport.lintRule(ruleProvider, "fun outer() { fun inner() {} }\n", enabledConfig).size)
    }

    @Test
    fun excludesLocalClass() {
        assertTrue(
            RuleTestSupport.lintRule(
                ruleProvider,
                "/** Doc */\nfun outer() { class Inner }\n",
                enabledConfig
            ).isEmpty()
        )
    }

    @Test
    fun flagsUndocumentedPublicProperty() {
        assertEquals(1, RuleTestSupport.lintRule(ruleProvider, "val x: Int = 1\n", enabledConfig).size)
    }

    @Test
    fun acceptsDocumentedPublicProperty() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "/** Doc */\nval x: Int = 1\n", enabledConfig).isEmpty())
    }

    @Test
    fun flagsUndocumentedProtectedMemberFunction() {
        assertEquals(1, RuleTestSupport.lintRule(ruleProvider, "/** Foo */\nclass Foo { protected fun bar() {} }\n", enabledConfig).size)
    }

    @Test
    fun excludesPrivateMemberFunction() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "/** Foo */\nclass Foo { private fun bar() {} }\n", enabledConfig).isEmpty())
    }

    @Test
    fun excludesMemberOfPrivateClass() {
        assertTrue(
            RuleTestSupport.lintRule(
                ruleProvider,
                "private class Outer { class Inner }\n",
                enabledConfig
            ).isEmpty()
        )
    }

    @Test
    fun flagsUndocumentedObjectDeclaration() {
        assertEquals(1, RuleTestSupport.lintRule(ruleProvider, "object Config\n", enabledConfig).size)
    }

    @Test
    fun excludesCompanionObject() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "/** Foo */\nclass Foo { companion object }\n", enabledConfig).isEmpty())
    }

    @Test
    fun isDormantForScripts() {
        assertTrue(RuleTestSupport.lintRule(ruleProvider, "class Foo\n", enabledConfig, "Sample.kts").isEmpty())
    }
}
