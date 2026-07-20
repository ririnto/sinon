package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicDeclarationDocCommentKtlintRuleTest {
    private val ruleProvider = RuleProvider { PublicDeclarationDocCommentKtlintRule() }
    private val enabledConfig = "[*]\nktlint_public_declaration_doc_comment_mode = on\n"
    private val publicConfig = "[*]\nktlint_public_declaration_doc_comment_mode = public\n"
    private val disabledConfig = "[*]\nktlint_public_declaration_doc_comment_mode = off\n"

    @Test
    fun isDisabledByDefault() {
        val source = "class Foo\n"

        assertTrue(lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun flagsMissingClassDocumentationWhenModeIsOn() {
        val source = "class Foo\n"
        val errors = lintRule(ruleProvider, source, enabledConfig)

        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source, enabledConfig))
    }

    @Test
    fun flagsMissingClassDocumentationWhenModeIsPublic() {
        val errors = lintRule(ruleProvider, "class Foo\n", publicConfig)

        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
    }

    @Test
    fun remainsDisabledWhenModeIsOff() {
        assertTrue(lintRule(ruleProvider, "class Foo\n", disabledConfig).isEmpty())
    }

    @Test
    fun acceptsDocumentedClass() {
        assertTrue(lintRule(ruleProvider, "/** Doc */\nclass Foo\n", enabledConfig).isEmpty())
    }

    @Test
    fun flagsUndocumentedTopLevelFunction() {
        assertEquals(1, lintRule(ruleProvider, "fun foo() {}\n", enabledConfig).size)
    }

    @Test
    fun acceptsDocumentedTopLevelFunction() {
        assertTrue(lintRule(ruleProvider, "/** Doc */\nfun foo() {}\n", enabledConfig).isEmpty())
    }

    @Test
    fun excludesOverrideFunction() {
        val source = "/** Base */\nopen class Base { /** Foo */ open fun foo() {} }\n/** Derived */\nclass Derived : Base() { override fun foo() {} }\n"

        assertTrue(lintRule(ruleProvider, source, enabledConfig).isEmpty())
    }

    @Test
    fun excludesLocalFunction() {
        assertEquals(1, lintRule(ruleProvider, "fun outer() { fun inner() {} }\n", enabledConfig).size)
    }

    @Test
    fun flagsUndocumentedPublicProperty() {
        assertEquals(1, lintRule(ruleProvider, "val x: Int = 1\n", enabledConfig).size)
    }

    @Test
    fun acceptsDocumentedPublicProperty() {
        assertTrue(lintRule(ruleProvider, "/** Doc */\nval x: Int = 1\n", enabledConfig).isEmpty())
    }

    @Test
    fun flagsUndocumentedProtectedMemberFunction() {
        val source = "/** Foo */\nclass Foo { protected fun bar() {} }\n"

        assertEquals(1, lintRule(ruleProvider, source, enabledConfig).size)
    }

    @Test
    fun excludesPrivateMemberFunction() {
        assertTrue(lintRule(ruleProvider, "/** Foo */\nclass Foo { private fun bar() {} }\n", enabledConfig).isEmpty())
    }

    @Test
    fun isDormantForScripts() {
        assertTrue(lintRule(ruleProvider, "class Foo\n", enabledConfig, "Sample.kts").isEmpty())
    }

    @Test
    fun formattingIsIdempotentAndLintOnly() {
        val source = "fun foo() {}\n"
        val formatted = formatRule(ruleProvider, source, enabledConfig)

        assertEquals(source, formatted)
        assertEquals(formatted, formatRule(ruleProvider, formatted, enabledConfig))
        assertEquals(1, lintRule(ruleProvider, formatted, enabledConfig).size)
    }
}
