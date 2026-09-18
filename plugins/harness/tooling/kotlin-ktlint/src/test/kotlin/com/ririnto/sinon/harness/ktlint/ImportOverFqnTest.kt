@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class ImportOverFqnTest {
    private val assertThat = assertThatRule { ImportOverFqn() }

    @Test
    fun simpleFqnIsRewrittenAndImported() {
        assertThat("val value = kotlin.collections.ArrayList<String>()\n")
            .hasLintViolation(
                1,
                13,
                "fully qualified name `kotlin.collections.ArrayList` used inline; add an import and use the simple name"
            ).isFormattedAs("import kotlin.collections.ArrayList\n\nval value = ArrayList<String>()\n")
    }

    @Test
    fun multiplePackagesAddMultipleImports() {
        assertThat("val a = java.util.ArrayList<String>()\nval b = kotlin.collections.LinkedList<String>()\n")
            .hasLintViolations(
                LintViolation(
                    1,
                    9,
                    "fully qualified name `java.util.ArrayList` used inline; add an import and use the simple name"
                ),
                LintViolation(
                    2,
                    9,
                    "fully qualified name `kotlin.collections.LinkedList` used inline; add an import and use the simple name"
                )
            ).isFormattedAs(
                "import java.util.ArrayList\nimport kotlin.collections.LinkedList\n\nval a = ArrayList<String>()\nval b = LinkedList<String>()\n"
            )
    }

    @Test
    fun insertsNewImportInAlphabeticalOrderWithExistingImports() {
        assertThat("import gamma.delta.Baz\n\nval value = alpha.beta.Foo()\n")
            .hasLintViolation(3, 13, "fully qualified name `alpha.beta.Foo` used inline; add an import and use the simple name")
            .isFormattedAs("import alpha.beta.Foo\nimport gamma.delta.Baz\n\nval value = Foo()\n")
    }

    @Test
    fun localNameCollisionIsLintOnly() {
        assertThat("fun test() { val ArrayList = 1; println(java.util.ArrayList<String>()) }\n")
            .hasLintViolationWithoutAutoCorrect(
                1,
                41,
                "fully qualified name `java.util.ArrayList` used inline; add an import and use the simple name"
            )
    }

    @Test
    fun sameNameImportIsLintOnly() {
        assertThat("import other.ArrayList\nval value = java.util.ArrayList<String>()\n")
            .hasLintViolationWithoutAutoCorrect(
                2,
                13,
                "fully qualified name `java.util.ArrayList` used inline; add an import and use the simple name"
            )
    }

    @Test
    fun samePathImportAllowsShortening() {
        assertThat("import kotlin.collections.ArrayList\n\nval value = kotlin.collections.ArrayList<String>()\n")
            .hasLintViolation(
                3,
                13,
                "fully qualified name `kotlin.collections.ArrayList` used inline; add an import and use the simple name"
            ).isFormattedAs("import kotlin.collections.ArrayList\n\nval value = ArrayList<String>()\n")
    }

    @Test
    fun samePathAliasImportDoesNotAllowShortening() {
        assertThat("import kotlin.collections.ArrayList as JList\n\nval value = kotlin.collections.ArrayList<String>()\n")
            .hasLintViolationWithoutAutoCorrect(
                3,
                13,
                "fully qualified name `kotlin.collections.ArrayList` used inline; add an import and use the simple name"
            )
    }

    @Test
    fun distinctFqnsWithSameSimpleNameStayLintOnly() {
        assertThat("val first = alpha.one.Widget()\nval second = beta.two.Widget()\n")
            .hasLintViolationsWithoutAutoCorrect(
                LintViolation(1, 13, "fully qualified name `alpha.one.Widget` used inline; add an import and use the simple name"),
                LintViolation(2, 14, "fully qualified name `beta.two.Widget` used inline; add an import and use the simple name")
            )
    }

    @Test
    fun ownPackageIsLintOnly() {
        assertThat("package java.util\n\nval value = java.util.ArrayList<String>()\n")
            .hasLintViolationWithoutAutoCorrect(
                3,
                13,
                "fully qualified name `java.util.ArrayList` used inline; add an import and use the simple name"
            )
    }

    @Test
    fun starImportConflictIsLintOnly() {
        assertThat("import other.*\nval value = java.util.ArrayList<String>()\n")
            .hasLintViolationWithoutAutoCorrect(
                2,
                13,
                "fully qualified name `java.util.ArrayList` used inline; add an import and use the simple name"
            )
    }

    @Test
    fun alreadyShortNameIsNoOp() {
        assertThat("val value = ArrayList<String>()\n").hasNoLintViolations()
    }
}
