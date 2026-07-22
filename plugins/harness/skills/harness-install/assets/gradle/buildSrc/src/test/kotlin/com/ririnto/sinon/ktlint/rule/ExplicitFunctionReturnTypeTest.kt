package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExplicitFunctionReturnTypeTest {
    private val ruleProvider: RuleProvider = RuleProvider { ExplicitFunctionReturnType() }

    @Test
    fun autocorrectsBareLiterals() {
        val cases = listOf(
            "fun greeting() = \"hi\"" to "fun greeting(): String = \"hi\"",
            "fun flag() = true" to "fun flag(): Boolean = true",
            "fun first() = 'a'" to "fun first(): Char = 'a'",
            "fun count() = 42" to "fun count(): Int = 42",
            "fun overflow() = 3000000000" to "fun overflow(): Long = 3000000000",
            "fun bigCount() = 42L" to "fun bigCount(): Long = 42L",
            "fun ratio() = 3.14" to "fun ratio(): Double = 3.14",
            "fun precise() = 3.14f" to "fun precise(): Float = 3.14f",
            "fun doc() = \"\"\"x\"\"\"" to "fun doc(): String = \"\"\"x\"\"\""
        )
        cases.forEach { (sourceLine, expectedLine) ->
            val source = "$sourceLine\n"
            val errors = RuleTestSupport.lintRule(ruleProvider, source)
            assertEquals(1, errors.size, sourceLine)
            assertTrue(errors.single().canBeAutoCorrected, sourceLine)
            assertEquals("$expectedLine\n", RuleTestSupport.formatRule(ruleProvider, source), sourceLine)
        }
    }

    @Test
    fun autocorrectIsIdempotent() {
        listOf("fun greeting() = \"hi\"\n", "fun count() = 42\n").forEach { source ->
            val once = RuleTestSupport.formatRule(ruleProvider, source)
            assertEquals(once, RuleTestSupport.formatRule(ruleProvider, once))
            assertTrue(RuleTestSupport.lintRule(ruleProvider, once).isEmpty())
        }
    }

    @Test
    fun leavesUnsafeShapesAndDeclarationsLintOnly() {
        val cases = listOf(
            "fun compute() = calculate()",
            "override fun name() = \"x\"",
            "fun neg() = -1",
            "fun composed() = if (b) 1 else 2"
        )
        cases.forEach { line ->
            val source = "$line\n"
            val errors = RuleTestSupport.lintRule(ruleProvider, source)
            assertEquals(1, errors.size, line)
            assertFalse(errors.single().canBeAutoCorrected, line)
            assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source), line)
        }
    }

    @Test
    fun unsignedLongLiteralStaysLintOnly() {
        val source = "fun count() = 1UL\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(errors.single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun ignoresAlreadyTypedAndBlockFunctions() {
        val source = "fun already(): Int = 42\nfun block() { val x = 1 }\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun autocorrectsMemberFunction() {
        val source = "class C { fun name() = \"x\" }\n"
        val expected = "class C { fun name(): String = \"x\" }\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
        assertEquals(expected, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun ignoresExplicitUnitExpressionBody() {
        val source = "fun noop() = Unit\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun ignoresExplicitKotlinUnitExpressionBody() {
        val source = "fun noop() = kotlin.Unit\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun stripsExplicitUnitReturnTypeFromExpressionBody() {
        val source = "fun noop(): Unit = Unit\n"
        val expected = "fun noop() = Unit\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
        assertEquals(expected, RuleTestSupport.formatRule(ruleProvider, source))
        assertTrue(RuleTestSupport.lintRule(ruleProvider, expected).isEmpty())
        assertEquals(expected, RuleTestSupport.formatRule(ruleProvider, expected))
    }

    @Test
    fun stripsExplicitKotlinUnitReturnType() {
        val source = "fun noop(): kotlin.Unit {}\n"
        val expected = "fun noop() {}\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
        assertEquals(expected, RuleTestSupport.formatRule(ruleProvider, source))
        assertTrue(RuleTestSupport.lintRule(ruleProvider, expected).isEmpty())
    }

    @Test
    fun stripsExplicitUnitReturnTypeFromBlockBody() {
        val source = "fun noop(): Unit { println(\"hi\") }\n"
        val expected = "fun noop() { println(\"hi\") }\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
        assertEquals(expected, RuleTestSupport.formatRule(ruleProvider, source))
        assertTrue(RuleTestSupport.lintRule(ruleProvider, expected).isEmpty())
        assertEquals(expected, RuleTestSupport.formatRule(ruleProvider, expected))
    }

    @Test
    fun autocorrectsFunctionWithDefaultParameter() {
        val source = "fun greet(prefix: String = \"hi\") = \"hello\"\n"
        val expected = "fun greet(prefix: String = \"hi\"): String = \"hello\"\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
        assertEquals(expected, RuleTestSupport.formatRule(ruleProvider, source))
        assertTrue(RuleTestSupport.lintRule(ruleProvider, expected).isEmpty())
    }

    @Test
    fun autocorrectsFunctionWithAnnotationArgument() {
        val source = "@Ann(enabled = true)\nfun greet() = \"hello\"\n"
        val expected = "@Ann(enabled = true)\nfun greet(): String = \"hello\"\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
        assertEquals(expected, RuleTestSupport.formatRule(ruleProvider, source))
        assertTrue(RuleTestSupport.lintRule(ruleProvider, expected).isEmpty())
    }

    @Test
    fun stripsUnitReturnTypePreservingWhitespaceBeforeColon() {
        val source = "fun noop() : Unit {}\n"
        val expected = "fun noop() {}\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
        assertEquals(expected, RuleTestSupport.formatRule(ruleProvider, source))
        assertTrue(RuleTestSupport.lintRule(ruleProvider, expected).isEmpty())
    }
}
