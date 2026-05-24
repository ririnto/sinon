package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.PsiFinding
import kotlinx.serialization.json.JsonObject
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.nio.file.Path

/**
 * Base class for harness validation rules.
 */
abstract class HarnessCheckRule {
    /** Category key used in the harness manifest and findings. */
    abstract val category: String

    /**
     * Check whether this rule applies based on manifest configuration.
     *
     * @param manifest The harness validation manifest.
     * @return true if the rule is enabled, false otherwise.
     */
    abstract fun applies(manifest: JsonObject): Boolean

    /**
     * Validate and return a collection of findings.
     *
     * @param manifest The harness validation manifest.
     * @param root The root path of the project.
     * @return Collection of findings; empty collection if no issues found.
     */
    abstract fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding>

    /** Render PSI scanner details owned by this rule into final findings. */
    abstract fun renderPsiFindings(
        findings: List<PsiFinding>,
        manifest: JsonObject,
    ): Collection<Finding>

    /** Return raw PSI findings produced directly by this rule. */
    abstract fun findPsiFindings(
        file: Path,
        root: Path,
        psiFactory: KtPsiFactory?,
    ): List<PsiFinding>
}
