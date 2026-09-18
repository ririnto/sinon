@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class NoImportAliasTest {
    private val assertThat = assertThatRule { NoImportAlias() }

    @Test
    fun aliasMatchingSimpleNameIsFlagged() {
        assertThat("import a.Foo as Foo\n")
            .hasLintViolationWithoutAutoCorrect(1, 17, "remove the import alias `Foo` that duplicates the imported simple name")
    }

    @Test
    fun aliasDifferentFromSimpleNameIsAllowed() {
        assertThat("import a.Foo as Bar\n").hasNoLintViolations()
    }

    @Test
    fun importWithoutAliasIsNotFlagged() {
        assertThat("import a.Foo\n").hasNoLintViolations()
    }
}
