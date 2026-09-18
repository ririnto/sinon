@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class ExplicitPropertyTypeTest {
    private val assertThat = assertThatRule { ExplicitPropertyType() }

    @Test
    fun autocorrectsLiteralMemberProperties() {
        val cases =
            listOf(
                "class C { val count = 42 }" to "class C { val count: Int = 42 }",
                "class C { val overflow = 3000000000 }" to "class C { val overflow: Long = 3000000000 }",
                "class C { val id = 42L }" to "class C { val id: Long = 42L }",
                "class C { val enabled = true }" to "class C { val enabled: Boolean = true }",
                "class C { val name = \"foo\" }" to "class C { val name: String = \"foo\" }",
                "class C { val sep = ',' }" to "class C { val sep: Char = ',' }",
                "class C { val r = 3.14f }" to "class C { val r: Float = 3.14f }",
                "class C { val pi = 3.14 }" to "class C { val pi: Double = 3.14 }"
            )
        cases.forEach { (source, expected) ->
            val name = source.substringAfter("val ").substringBefore(" =")
            val offset = source.indexOf(name) + 1
            assertThat(source)
                .hasLintViolation(1, offset, "declare an explicit type on member property `$name`")
                .isFormattedAs(expected)
        }
    }

    @Test
    fun autocorrectsCompanionObjectProperty() {
        val source = "class C { companion object { val n = 42 } }"
        assertThat(source)
            .hasLintViolation(1, 34, "declare an explicit type on companion object property `n`")
            .isFormattedAs("class C { companion object { val n: Int = 42 } }")
    }

    @Test
    fun autocorrectsObjectProperty() {
        assertThat("object O { val n = 42 }")
            .hasLintViolation(1, 16, "declare an explicit type on member property `n`")
            .isFormattedAs("object O { val n: Int = 42 }")
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
            assertThat(source)
                .hasLintViolationWithoutAutoCorrect(1, 15, "declare an explicit type on member property `x`")
        }
    }

    @Test
    fun flagsOnlyUntypedMemberProperties() {
        listOf(
            "fun f() { val x = 42 }",
            "val top = 42",
            "class C { val x: Int = 42 }"
        ).forEach { source ->
            assertThat(source).hasNoLintViolations()
        }
    }

    @Test
    fun autocorrectsPropertyWithAnnotationReferencingName() {
        val source = "class C { @Ann(name = \"name\") val name = 42 }"
        assertThat(source)
            .hasLintViolation(1, 35, "declare an explicit type on member property `name`")
            .isFormattedAs("class C { @Ann(name = \"name\") val name: Int = 42 }")
    }
}
