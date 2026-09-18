@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class MultilineKdocTest {
    private val assertThat = assertThatRule { MultilineKdoc() }

    @Test
    fun expandsSingleLineKdoc() {
        val source = "/** Contract. */\nclass Service\n"
        val expected =
            """
            /**
             * Contract.
             */
            class Service
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolation(1, 1, "use multiline KDoc for this declaration")
            .isFormattedAs(expected)
    }

    @Test
    fun preservesAlreadyMultilineKdoc() {
        val source =
            """
            /**
             * Contract.
             */
            class Service
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }
}
