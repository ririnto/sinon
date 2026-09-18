@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class NullableElvisReturnTest {
    private val assertThat = assertThatRule { NullableElvisReturn() }

    @Test
    fun mapLookupWithReturnFallbackIsFlagged() {
        val source =
            """
            class Example {
                fun value(values: Map<String, String>, key: String): String {
                    val value = values[key] ?: return "fallback"
                    return value
                }
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(
                3,
                13,
                "Return nullable lookups as an expression with let and an explicit parameter"
            )
    }

    @Test
    fun safeCallPropertyLookupWithReturnFallbackIsFlagged() {
        val source =
            """
            class Example {
                fun value(example: Example?): String {
                    val value = example.field ?: return "fallback"
                    return value
                }

                val field: String
                    get() = "value"
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(
                3,
                13,
                "Return nullable lookups as an expression with let and an explicit parameter"
            )
    }

    @Test
    fun expressionBodyElvisIsSafe() {
        assertThat("class Example {\n    fun value(value: String?): String = value ?: \"fallback\"\n}\n")
            .hasNoLintViolations()
    }

    @Test
    fun elvisWithoutReturnIsSafe() {
        assertThat("class Example {\n    val value: String = compute() ?: \"fallback\"\n}\n")
            .hasNoLintViolations()
    }
}
