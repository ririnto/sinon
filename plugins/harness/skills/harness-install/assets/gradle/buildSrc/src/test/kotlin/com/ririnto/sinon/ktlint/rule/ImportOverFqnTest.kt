package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportOverFqnTest {
    private val ruleProvider: RuleProvider = RuleProvider { ImportOverFqn() }

    @Test
    fun simpleFqnIsRewrittenAndImported() {
        val source = "val value = kotlin.collections.ArrayList<String>()\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
        assertEquals(
            "import kotlin.collections.ArrayList\n\nval value = ArrayList<String>()\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun multipleFqnsFromOnePackageShareImport() {
        val source = "val a = java.util.ArrayList<String>()\nval b = java.util.HashMap<String, String>()\n"
        val formatted = RuleTestSupport.formatRule(ruleProvider, source)
        assertTrue(formatted.contains("import java.util.ArrayList\nimport java.util.HashMap\n"))
        assertFalse(formatted.lines().drop(2).any { line -> "java.util." in line })
    }

    @Test
    fun multiplePackagesAddMultipleImports() {
        val source = "val a = java.util.ArrayList<String>()\nval b = kotlin.collections.LinkedList<String>()\n"
        val formatted = RuleTestSupport.formatRule(ruleProvider, source)
        assertTrue(formatted.contains("import java.util.ArrayList\nimport kotlin.collections.LinkedList\n"))
    }

    @Test
    fun insertsNewImportInAlphabeticalOrderWithExistingImports() {
        val source = "import gamma.delta.Baz\n\nval value = alpha.beta.Foo()\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
        assertEquals(
            "import alpha.beta.Foo\nimport gamma.delta.Baz\n\nval value = Foo()\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun insertsNewImportBetweenExistingImports() {
        val source = "import alpha.alpha.A\nimport gamma.gamma.C\n\nval value = beta.beta.B()\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
        assertEquals(
            "import alpha.alpha.A\nimport beta.beta.B\nimport gamma.gamma.C\n\nval value = B()\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun localNameCollisionIsLintOnly() {
        val source = "fun test() { val ArrayList = 1; println(java.util.ArrayList<String>()) }\n"
        assertFalse(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun sameNameImportIsLintOnly() {
        val source = "import other.ArrayList\nval value = java.util.ArrayList<String>()\n"
        assertFalse(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun samePathImportAllowsShortening() {
        val source = "import kotlin.collections.ArrayList\n\nval value = kotlin.collections.ArrayList<String>()\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
        assertEquals(
            "import kotlin.collections.ArrayList\n\nval value = ArrayList<String>()\n",
            RuleTestSupport.formatRule(ruleProvider, source)
        )
    }

    @Test
    fun samePathAliasImportDoesNotAllowShortening() {
        val source = "import kotlin.collections.ArrayList as JList\n\nval value = kotlin.collections.ArrayList<String>()\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(1, errors.size)
        assertFalse(
            "aliased import does not bind the unaliased name, must stay lint-only: ${errors}"
        ) { errors.single().canBeAutoCorrected }
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun distinctFqnsWithSameSimpleNameStayLintOnly() {
        val source = "val first = alpha.one.Widget()\nval second = beta.two.Widget()\n"
        val errors = RuleTestSupport.lintRule(ruleProvider, source)
        assertEquals(2, errors.size)
        errors.forEach { error ->
            assertFalse(
                "same simple name from distinct FQNs must stay lint-only: ${error}"
            ) { error.canBeAutoCorrected }
        }
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }

    @Test
    fun ownPackageIsLintOnly() {
        val source = "package java.util\n\nval value = java.util.ArrayList<String>()\n"
        assertFalse(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
    }

    @Test
    fun starImportConflictIsLintOnly() {
        val source = "import other.*\nval value = java.util.ArrayList<String>()\n"
        assertFalse(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
    }

    @Test
    fun aliasImportConflictIsLintOnly() {
        val source = "import other.ArrayList as ArrayList\nval value = java.util.ArrayList<String>()\n"
        assertFalse(RuleTestSupport.lintRule(ruleProvider, source).single().canBeAutoCorrected)
    }

    @Test
    fun formattingIsIdempotent() {
        val formatted = RuleTestSupport.formatRule(ruleProvider, "val value = kotlin.collections.ArrayList<String>()\n")
        assertEquals(formatted, RuleTestSupport.formatRule(ruleProvider, formatted))
    }

    @Test
    fun alreadyShortNameIsNoOp() {
        val source = "val value = ArrayList<String>()\n"
        assertTrue(RuleTestSupport.lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, RuleTestSupport.formatRule(ruleProvider, source))
    }
}
