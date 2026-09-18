@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class KotlinTopLevelDeclarationCountTest {
    private val assertThat = assertThatRule { KotlinTopLevelDeclarationCount() }

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
            assertThat("$declarationSource\n").hasNoLintViolations()
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
            assertThat(source).hasLintViolationWithoutAutoCorrect(1, 1, "declare a single top-level type declaration in this file")
        }
    }

    @Test
    fun ignoresKotlinScripts() {
        val source =
            """
            val first = 1
            val second = 2
            """.trimIndent() + "\n"
        assertThat(source).asKotlinScript(true).hasNoLintViolations()
    }
}
