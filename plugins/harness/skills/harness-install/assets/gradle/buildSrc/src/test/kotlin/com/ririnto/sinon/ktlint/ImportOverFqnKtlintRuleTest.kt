package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportOverFqnKtlintRuleTest {
    private val ruleProvider = RuleProvider { ImportOverFqnKtlintRule() }

    @Test
    fun simpleFqnIsRewrittenAndImported() {
        val source = "val value = kotlin.collections.ArrayList<String>()\n"
        val errors = lintRule(ruleProvider, source)
        assertTrue(errors.single().canBeAutoCorrected)
        assertEquals(
            "import kotlin.collections.ArrayList\n\nval value = ArrayList<String>()\n",
            formatRule(ruleProvider, source)
        )
    }

    @Test
    fun multipleFqnsFromOnePackageShareImport() {
        val source = "val a = java.util.ArrayList<String>()\nval b = java.util.HashMap<String, String>()\n"
        val formatted = formatRule(ruleProvider, source)
        assertTrue(formatted.contains("import java.util.ArrayList\nimport java.util.HashMap\n"))
        assertFalse(formatted.lines().drop(2).any { line -> "java.util." in line })
    }

    @Test
    fun multiplePackagesAddMultipleImports() {
        val source = "val a = java.util.ArrayList<String>()\nval b = kotlin.collections.LinkedList<String>()\n"
        val formatted = formatRule(ruleProvider, source)
        assertTrue(formatted.contains("import java.util.ArrayList\nimport kotlin.collections.LinkedList\n"))
    }

    @Test
    fun localNameCollisionIsLintOnly() {
        val source = "fun test() { val ArrayList = 1; println(java.util.ArrayList<String>()) }\n"
        val error = lintRule(ruleProvider, source).single()
        assertFalse(error.canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun sameNameImportIsLintOnly() {
        val source = "import other.ArrayList\nval value = java.util.ArrayList<String>()\n"
        assertFalse(lintRule(ruleProvider, source).single().canBeAutoCorrected)
        assertEquals(source, formatRule(ruleProvider, source))
    }

    @Test
    fun ownPackageIsLintOnly() {
        val source = "package java.util\n\nval value = java.util.ArrayList<String>()\n"
        assertFalse(lintRule(ruleProvider, source).single().canBeAutoCorrected)
    }

    @Test
    fun starImportConflictIsLintOnly() {
        val source = "import other.*\nval value = java.util.ArrayList<String>()\n"
        assertFalse(lintRule(ruleProvider, source).single().canBeAutoCorrected)
    }

    @Test
    fun aliasImportConflictIsLintOnly() {
        val source = "import other.ArrayList as ArrayList\nval value = java.util.ArrayList<String>()\n"
        assertFalse(lintRule(ruleProvider, source).single().canBeAutoCorrected)
    }

    @Test
    fun formattingIsIdempotent() {
        val source = "val value = kotlin.collections.ArrayList<String>()\n"
        val formatted = formatRule(ruleProvider, source)
        assertEquals(formatted, formatRule(ruleProvider, formatted))
    }

    @Test
    fun alreadyShortNameIsNoOp() {
        val source = "val value = ArrayList<String>()\n"
        assertTrue(lintRule(ruleProvider, source).isEmpty())
        assertEquals(source, formatRule(ruleProvider, source))
    }
}
