@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class CompanionObjectPositionTest {
    private val assertThat = assertThatRule { CompanionObjectPosition() }

    @Test
    fun flagsCompanionObjectAfterOtherDeclarations() {
        val source =
            """
            class Example {
                val value: String = "value"

                companion object
            }
            """.trimIndent() + "\n"
        assertThat(source).hasLintViolationWithoutAutoCorrect(4, 15, "place the companion object before other class members")
    }

    @Test
    fun acceptsCompanionObjectAtFirstPosition() {
        val source =
            """
            class Example {
                companion object

                val value: String = "value"
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }

    @Test
    fun acceptsClassWithoutCompanionObject() {
        val source =
            """
            class Example {
                val value: String = "value"
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }

    @Test
    fun ignoresEnumEntriesWhenComputingFirstPosition() {
        val source =
            """
            enum class Status {
                ACTIVE;

                companion object {
                    const val DEFAULT = "active"
                }
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }

    @Test
    fun checksNestedClassesIndependently() {
        val source =
            """
            class Outer {
                val outerValue: String = "outer"

                class Inner {
                    val innerValue: String = "inner"

                    companion object
                }
            }
            """.trimIndent() + "\n"
        assertThat(source).hasLintViolationWithoutAutoCorrect(7, 19, "place the companion object before other class members")
    }
}
