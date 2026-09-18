@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class PublicDeclarationDocCommentTest {
    private val assertThat = assertThatRule { PublicDeclarationDocComment() }

    @Test
    fun requiresDocumentationOnEffectivePublicDeclarations() {
        val source =
            """
            class Service(
                val dependency: String
            )
            """.trimIndent() + "\n"
        assertThat(source)
            .withEditorConfigOverride(PublicDeclarationDocComment.DOC_COMMENT_MODE to "on")
            .hasLintViolations(
                LintViolation(1, 7, "add a documentation comment to public declaration `Service`", false)
            )
    }

    @Test
    fun requiresDocumentationOnPublicInterfaces() {
        val source =
            """
            interface Repository {
                fun find(): String
            }
            """.trimIndent() + "\n"
        assertThat(source)
            .withEditorConfigOverride(PublicDeclarationDocComment.DOC_COMMENT_MODE to "on")
            .hasLintViolations(
                LintViolation(1, 11, "add a documentation comment to public declaration `Repository`", false),
                LintViolation(2, 9, "add a documentation comment to public declaration `find`", false)
            )
    }

    @Test
    fun acceptsDocumentationAndInheritedOverrideContracts() {
        val source =
            """
            /** Service contract. */
            open class Service {
                /** Performs work. */
                open fun work() {
                }
            }

            class Child : Service() {
                override fun work() {
                }
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }

    @Test
    fun ignoresDeclarationsHiddenByInternalOrPrivateEnclosures() {
        val source =
            """
            internal class Hidden {
                val value: String = "value"
            }

            private object Secret {
                fun work() {
                }
            }
            """.trimIndent() + "\n"
        assertThat(source).hasNoLintViolations()
    }
}
