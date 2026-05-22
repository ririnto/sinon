package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path

/**
 * Interface for harness validation rules.
 */
interface HarnessCheckRule {
	/**
	 * Check whether this rule applies based on manifest configuration.
	 *
	 * @param manifest The harness validation manifest.
	 * @return true if the rule is enabled, false otherwise.
	 */
	fun applies(manifest: JsonObject): Boolean

	/**
	 * Validate and return a list of findings.
	 *
	 * @param manifest The harness validation manifest.
	 * @param root The root path of the project.
	 * @param psiResults PSI analysis results, may be null.
	 * @return List of findings; empty list if no issues found.
	 */
	fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults? = null): List<Finding>
}
