package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinTopLevelDeclarationCountKtlintRuleTest {
    private val ruleProvider = RuleProvider { KotlinTopLevelDeclarationCountKtlintRule() }

    @Test
    fun allowsExactlyOneTopLevelTypeDeclaration() {
        listOf(
            "class Sample",
            "interface Sample",
            "object Sample",
            "enum class Sample { VALUE }",
            "annotation class Sample",
            "typealias Sample = String"
        ).forEach { declarationSource ->
            assertTrue(lintRule(ruleProvider, "$declarationSource\n").isEmpty())
        }
    }

    @Test
    fun rejectsZeroMultipleFunctionAndPropertyDeclarations() {
        listOf(
            "",
            "class First\nclass Second\n",
            "fun sample() = 42\n",
            "val sample = 42\n",
            "var sample = 42\n"
        ).forEach { source ->
            val errors = lintRule(ruleProvider, source)
            assertEquals(1, errors.size)
            assertEquals(RuleId("code:kotlin-top-level-declaration-count"), errors.single().ruleId)
            assertFalse(errors.single().canBeAutoCorrected)
        }
    }

    @Test
    fun ignoresKotlinScripts() {
        val source = """
            val first = 1
            val second = 2
        """.trimIndent() + "\n"

        assertTrue(lintRule(ruleProvider, source, fileName = "Sample.kts").isEmpty())
        assertEquals(source, formatRule(ruleProvider, source, fileName = "Sample.kts"))
    }
}
