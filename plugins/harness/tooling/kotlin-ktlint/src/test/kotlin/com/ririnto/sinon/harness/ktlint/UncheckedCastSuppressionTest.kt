@file:Suppress("ktlint:harness:explicit-property-type")

package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import org.junit.jupiter.api.Test

class UncheckedCastSuppressionTest {
    private val assertThat = assertThatRule { UncheckedCastSuppression() }

    @Test
    fun staleSuppressOnFunctionWithoutCastAutocorrectsByRemovingAnnotation() {
        assertThat("@Suppress(\"UNCHECKED_CAST\")\nfun sample(): Int = 42\n")
            .hasLintViolation(
                1,
                1,
                "avoid suppression of forbidden tokens (`@Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling"
            ).isFormattedAs("fun sample(): Int = 42\n")
    }

    @Test
    fun staleSuppressOnPropertyInitializerAutocorrects() {
        assertThat("@Suppress(\"UNCHECKED_CAST\")\nval sample: Int = 42\n")
            .hasLintViolation(
                1,
                1,
                "avoid suppression of forbidden tokens (`@Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling"
            ).isFormattedAs("val sample: Int = 42\n")
    }

    @Test
    fun staleFileLevelSuppressAutocorrectsAndRemovesFileAnnotationLine() {
        assertThat("@file:Suppress(\"UNCHECKED_CAST\")\n\npackage com.example\n\nfun sample(): Int = 42\n")
            .hasLintViolation(
                1,
                1,
                "avoid suppression of forbidden tokens (`@file:Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling"
            ).isFormattedAs("package com.example\n\nfun sample(): Int = 42\n")
    }

    @Test
    fun staleSuppressAmongOtherAnnotationsRemovesOnlyTheSuppressEntry() {
        assertThat("@Deprecated(\"old\")\n@Suppress(\"UNCHECKED_CAST\")\nfun sample(): Int = 42\n")
            .hasLintViolation(
                2,
                1,
                "avoid suppression of forbidden tokens (`@Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling"
            ).isFormattedAs("@Deprecated(\"old\")\n\nfun sample(): Int = 42\n")
    }

    @Test
    fun liveSuppressOnFunctionWithAsCastRemainsUncorrected() {
        assertThat("@Suppress(\"UNCHECKED_CAST\")\nfun sample(value: Any): String = value as String\n")
            .hasLintViolationWithoutAutoCorrect(
                1,
                1,
                "avoid suppression of forbidden tokens (`@Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling"
            )
    }

    @Test
    fun multiArgSuppressWithForbiddenTokenRemainsUncorrectedEvenWhenStale() {
        assertThat("@Suppress(\"UNCHECKED_CAST\", \"DEPRECATION\")\nfun sample(): Int = 42\n")
            .hasLintViolationWithoutAutoCorrect(
                1,
                1,
                "avoid suppression of forbidden tokens (`@Suppress(\"UNCHECKED_CAST\", \"DEPRECATION\")`); refactor to type-safe cast or explicit handling"
            )
    }

    @Test
    fun allowedTokenFilterSuppressesDetection() {
        assertThat("@Suppress(\"UNCHECKED_CAST\")\nfun sample(): Int = 42\n")
            .withEditorConfigOverride(UncheckedCastSuppression.ALLOWED_SUPPRESSIONS to "UNCHECKED_CAST")
            .hasNoLintViolations()
    }

    @Test
    fun customForbiddenTokenDetectedAndAutocorrectsWhenStale() {
        assertThat("@Suppress(\"USELESS_CAST\")\nfun sample(): Int = 42\n")
            .withEditorConfigOverride(UncheckedCastSuppression.FORBIDDEN_SUPPRESSIONS to "USELESS_CAST")
            .hasLintViolation(
                1,
                1,
                "avoid suppression of forbidden tokens (`@Suppress(\"USELESS_CAST\")`); refactor to type-safe cast or explicit handling"
            ).isFormattedAs("fun sample(): Int = 42\n")
    }

    @Test
    fun nonSuppressAnnotationIsIgnored() {
        assertThat("@Deprecated(\"use newSample instead\")\nfun sample(): Int = 42\n").hasNoLintViolations()
    }

    @Test
    fun fullyQualifiedSuppressAnnotationIsDetected() {
        assertThat("@kotlin.Suppress(\"UNCHECKED_CAST\")\nfun sample(): Int = 42\n")
            .hasLintViolation(
                1,
                1,
                "avoid suppression of forbidden tokens (`@kotlin.Suppress(\"UNCHECKED_CAST\")`); refactor to type-safe cast or explicit handling"
            ).isFormattedAs("fun sample(): Int = 42\n")
    }
}
