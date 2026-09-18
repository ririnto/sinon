@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class LeadingUnderscoreTest {
    private val assertThat = assertThatRule { LeadingUnderscore() }

    @Test
    fun autocorrectsUnreferencedPrivateFunctionParameter() {
        val source =
            """
            class Example {
                private fun compute(_unused: Int): Int = 42
            }
            """.trimIndent() + "\n"
        val expected =
            """
            class Example {
                private fun compute(_: Int): Int = 42
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolation(2, 25, "remove the leading underscore from declaration `_unused`")
            .isFormattedAs(expected)
    }

    @Test
    fun leavesLintOnlyWhenParameterIsReferencedInBody() {
        val source =
            """
            class Example {
                private fun compute(_value: Int): Int = _value + 1
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(2, 25, "remove the leading underscore from declaration `_value`")
    }

    @Test
    fun leavesLintOnlyWhenFunctionIsPublic() {
        val source =
            """
            class Example {
                fun compute(_unused: Int): Int = 42
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(2, 17, "remove the leading underscore from declaration `_unused`")
    }

    @Test
    fun acceptsParameterWithoutLeadingUnderscore() {
        val source =
            """
            class Example {
                private fun compute(value: Int): Int = value + 1
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }

    @Test
    fun leavesLintOnlyWhenParameterReferencedInSiblingDefaultValue() {
        val source =
            """
            class Example {
                private fun compute(_base: Int, other: Int = _base): Int = other
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(2, 25, "remove the leading underscore from declaration `_base`")
    }

    @Test
    fun leavesLintOnlyWhenCalledWithNamedArgument() {
        val source =
            """
            class Example {
                private fun compute(_unused: Int): Int = 42

                fun caller(): Int = compute(_unused = 5)
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(2, 25, "remove the leading underscore from declaration `_unused`")
    }

    @Test
    fun leavesLintOnlyWhenParameterIsValInPrimaryConstructor() {
        val source = "class Example(private val _id: Int)\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(1, 27, "remove the leading underscore from declaration `_id`")
    }

    @Test
    fun leavesLintOnlyWhenDeclarationIsProperty() {
        val source =
            """
            class Example {
                private val _value: Int = 42
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(2, 17, "remove the leading underscore from declaration `_value`")
    }

    @Test
    fun acceptsOverrideParameterWithLeadingUnderscore() {
        val source =
            """
            interface Base {
                fun compute(_unused: Int): Int
            }

            class Impl : Base {
                override fun compute(_unused: Int): Int = 0
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }

    @Test
    fun acceptsOverridePropertyWithLeadingUnderscore() {
        val source =
            """
            open class Base {
                open val _value: Int = 0
            }

            class Derived : Base() {
                override val _value: Int = 1
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }

    @Test
    fun rejectsNonOverridePropertyWithLeadingUnderscore() {
        val source =
            """
            class C {
                val _value: Int = 0
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(2, 9, "remove the leading underscore from declaration `_value`")
    }
}
