@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class NoJavaPathApiTest {
    private val assertThat = assertThatRule { NoJavaPathApi() }

    @Test
    fun filesImportIsFlagged() {
        assertThat("import java.nio.file.Files\n\nclass Example\n")
            .hasLintViolationWithoutAutoCorrect(1, 1, "Use kotlin.io.path APIs instead of java.nio.file.Files helpers")
    }

    @Test
    fun fileToHelperPropertyChainsAreFlagged() {
        val source =
            """
            import java.nio.file.Path

            class Example(val path: Path) {
                val isDirectory = path.toFile().isDirectory
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(4, 23, "Use kotlin.io.path APIs instead of File path helpers")
    }

    @Test
    fun sameNamedLocalClassesAreSafe() {
        val source =
            """
            class Files {
                fun readAllBytes(): ByteArray = byteArrayOf()
            }

            class Path {
                fun of(value: String): Path = this
            }

            fun use(files: Files, path: Path) {
                files.readAllBytes()
                path.of("value")
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }
}
