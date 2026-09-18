@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.pinterest.ktlint.test.LintViolation
import org.junit.jupiter.api.Test

class NoLineCommentTest {
    private val assertThat = assertThatRule { NoLineComment() }

    @Test
    fun standaloneLineCommentIsFlagged() {
        assertThat("fun foo() {\n    // comment\n}\n")
            .hasLintViolationWithoutAutoCorrect(2, 5, "use KDoc (/** ... */) instead of // or /* */ comments")
    }

    @Test
    fun trailingLineCommentIsFlagged() {
        assertThat("fun foo() {\n    val x = 1 // comment\n}\n")
            .hasLintViolationWithoutAutoCorrect(2, 15, "use KDoc (/** ... */) instead of // or /* */ comments")
    }

    @Test
    fun blockCommentIsFlagged() {
        assertThat("fun foo() {\n    /* block */\n}\n")
            .hasLintViolationWithoutAutoCorrect(2, 5, "use KDoc (/** ... */) instead of // or /* */ comments")
    }

    @Test
    fun mixedLineAndBlockCommentsAreAllFlagged() {
        val source = "fun foo() {\n    // one\n    val x = 1 // two\n    /* three */ val y = 2\n}\n"
        assertThat(source).hasLintViolationsWithoutAutoCorrect(
            LintViolation(2, 5, "use KDoc (/** ... */) instead of // or /* */ comments"),
            LintViolation(3, 15, "use KDoc (/** ... */) instead of // or /* */ comments"),
            LintViolation(4, 5, "use KDoc (/** ... */) instead of // or /* */ comments")
        )
    }

    @Test
    fun kdocCommentIsNotFlagged() {
        assertThat("/** docs */\nfun foo()\n").hasNoLintViolations()
    }

    @Test
    fun commentAtTopOfFileIsFlagged() {
        assertThat("// file-level comment\nfun foo()\n")
            .hasLintViolationWithoutAutoCorrect(1, 1, "use KDoc (/** ... */) instead of // or /* */ comments")
    }

    @Test
    fun lineCommentInsideLambdaBodyIsFlagged() {
        assertThat("fun foo() {\n    listOf(1).forEach {\n        // comment\n        it.inc()\n    }\n}\n")
            .hasLintViolationWithoutAutoCorrect(3, 9, "use KDoc (/** ... */) instead of // or /* */ comments")
    }

    @Test
    fun commentMarkersInsideRawStringAreIgnored() {
        assertThat("val text = \"\"\"// not a comment /* also not a comment */\"\"\"\n").hasNoLintViolations()
    }

    @Test
    fun formatLeavesCommentedSourceUnchanged() {
        val source = "fun foo() {\n    // comment\n}\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(2, 5, "use KDoc (/** ... */) instead of // or /* */ comments")
    }
}
