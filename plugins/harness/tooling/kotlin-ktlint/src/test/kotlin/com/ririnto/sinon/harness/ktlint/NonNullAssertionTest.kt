@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class NonNullAssertionTest {
    private val assertThat = assertThatRule { NonNullAssertion() }

    @Test
    fun autocorrectsRedundantNonNullAssertionOnRequireNotNull() {
        assertThat("fun sample(value: String?): String = requireNotNull(value)!!\n")
            .hasLintViolation(1, 59, "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard")
            .isFormattedAs("fun sample(value: String?): String = requireNotNull(value)\n")
    }

    @Test
    fun autocorrectsRedundantNonNullAssertionOnCheckNotNull() {
        assertThat("fun sample(value: String?): String = checkNotNull(value)!!\n")
            .hasLintViolation(1, 57, "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard")
            .isFormattedAs("fun sample(value: String?): String = checkNotNull(value)\n")
    }

    @Test
    fun autocorrectsBareVariableAssertionToRequireNotNull() {
        assertThat("fun sample(value: String?): String = value!!\n")
            .hasLintViolation(1, 43, "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard")
            .isFormattedAs("fun sample(value: String?): String = requireNotNull(value)\n")
    }

    @Test
    fun autocorrectsMemberAccessOnNullableReceiver() {
        assertThat("fun sample(value: String?): Int = value!!.length\n")
            .hasLintViolation(1, 40, "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard")
            .isFormattedAs("fun sample(value: String?): Int = requireNotNull(value).length\n")
    }

    @Test
    fun autocorrectsIndexAccess() {
        assertThat("fun sample(map: Map<String, String>): String = map[\"key\"]!!\n")
            .hasLintViolation(1, 58, "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard")
            .isFormattedAs("fun sample(map: Map<String, String>): String = requireNotNull(map[\"key\"])\n")
    }

    @Test
    fun autocorrectsMemberFunctionWithSimilarName() {
        val source =
            """
            class Service {
                fun requireNonNull(): String = "value"
            }
            fun sample(service: Service): String = service.requireNonNull()!!
            """.trimIndent() + "\n"
        val expected =
            """
            class Service {
                fun requireNonNull(): String = "value"
            }
            fun sample(service: Service): String = requireNotNull(service.requireNonNull())
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolation(4, 64, "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard")
            .isFormattedAs(expected)
    }

    @Test
    fun disablesAutocorrectWhenUserDefinedGuardShadowsStdlib() {
        val source =
            """
            fun requireNotNull(value: Any?): String = value.toString()
            fun sample(value: String?): String = value!!
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(
                2,
                43,
                "avoid non-null assertion `!!`; a user-defined `requireNotNull` shadows the standard library guard, so rewrite it manually with `requireNotNull(...)` or rename the shadow"
            )
    }

    @Test
    fun disablesAutocorrectWhenStarImportCouldProvideGuard() {
        val source =
            """
            import custom.guards.*

            fun sample(value: String?): String = value!!
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(
                3,
                43,
                "avoid non-null assertion `!!`; a user-defined `requireNotNull` shadows the standard library guard, so rewrite it manually with `requireNotNull(...)` or rename the shadow"
            )
    }

    @Test
    fun keepsAutocorrectWhenOnlyStdlibGuardIsImportedExplicitly() {
        val source = "import kotlin.requireNotNull\n\nfun sample(value: String?): String = value!!\n"
        val expected = "import kotlin.requireNotNull\n\nfun sample(value: String?): String = requireNotNull(value)\n"
        assertThat(source)
            .hasLintViolation(3, 43, "avoid non-null assertion `!!`; use safe call (?.), Elvis (?:), or an explicit `requireNotNull` guard")
            .isFormattedAs(expected)
    }

    @Test
    fun unaliasedCustomGuardImportDisablesAutocorrect() {
        val source =
            """
            import com.example.requireNotNull

            fun sample(value: Any?): Int = value!!
            """.trimIndent() + "\n"
        assertThat(source)
            .hasLintViolationWithoutAutoCorrect(
                3,
                37,
                "avoid non-null assertion `!!`; a user-defined `requireNotNull` shadows the standard library guard, so rewrite it manually with `requireNotNull(...)` or rename the shadow"
            )
    }
}
