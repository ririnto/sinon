package com.ririnto.sinon.ktlint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExplicitPropertyTypeKtlintRuleTest {
    private val ruleProvider = com.pinterest.ktlint.rule.engine.core.api.RuleProvider {
        ExplicitPropertyTypeKtlintRule()
    }

    @Test
    fun autocorrectsLiteralMemberProperties() {
        val cases = listOf(
            "class C { val count = 42 }" to "class C { val count: Int = 42 }",
            "class C { val id = 42L }" to "class C { val id: Long = 42L }",
            "class C { val enabled = true }" to "class C { val enabled: Boolean = true }",
            "class C { val name = \"foo\" }" to "class C { val name: String = \"foo\" }",
            "class C { val sep = ',' }" to "class C { val sep: Char = ',' }",
            "class C { val r = 3.14f }" to "class C { val r: Float = 3.14f }",
            "class C { val pi = 3.14 }" to "class C { val pi: Double = 3.14 }",
            "class C { val doc = \"\"\"x\"\"\" }" to "class C { val doc: String = \"\"\"x\"\"\" }",
            "class C { companion object { val n = 42 } }" to
                "class C { companion object { val n: Int = 42 } }",
            "object O { val n = 42 }" to "object O { val n: Int = 42 }"
        )

        cases.forEach { (source, expected) ->
            val errors = lintRule(ruleProvider, source)
            assertEquals(1, errors.size, source)
            assertTrue(errors.single().canBeAutoCorrected, source)
            assertEquals(expected, formatRule(ruleProvider, source), source)
        }
    }

    @Test
    fun autocorrectIsIdempotent() {
        listOf(
            "class C { val count = 42 }",
            "class C { val name = \"foo\" }"
        ).forEach { source ->
            val formatted = formatRule(ruleProvider, source)
            assertEquals(formatted, formatRule(ruleProvider, formatted))
            assertTrue(lintRule(ruleProvider, formatted).isEmpty())
        }
    }

    @Test
    fun leavesUnsafeInitializersLintOnly() {
        listOf(
            "class C { val x = compute() }",
            "class C { val x = -1 }",
            "class C { val x = 1 + 2 }",
            "class C { val x by lazy { 1 } }",
            "class C { val x = null }",
            "class C { val x = 1u }",
            "class C { val x = 1UL }"
        ).forEach { source ->
            val errors = lintRule(ruleProvider, source)
            assertEquals(1, errors.size, source)
            assertFalse(errors.single().canBeAutoCorrected, source)
            assertEquals(source, formatRule(ruleProvider, source), source)
        }
    }

    @Test
    fun flagsOnlyUntypedMemberProperties() {
        listOf(
            "fun f() { val x = 42 }",
            "val top = 42",
            "class C { val x: Int = 42 }"
        ).forEach { source ->
            assertTrue(lintRule(ruleProvider, source).isEmpty(), source)
            assertEquals(source, formatRule(ruleProvider, source), source)
        }
    }
}
