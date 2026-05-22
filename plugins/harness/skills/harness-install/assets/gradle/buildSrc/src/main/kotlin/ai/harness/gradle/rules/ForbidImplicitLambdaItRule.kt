package ai.harness.gradle.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.relativeTo
import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessCheckRule
import ai.harness.gradle.HarnessPsiResults

/**
 * Rule that forbids implicit lambda parameter 'it'.
 */
object ForbidImplicitLambdaItRule : HarnessCheckRule {
	/**
	 * PSI detection result for a single implicit lambda 'it' parameter.
	 */
	@Serializable
	data class Result(
		val file: String,
		val line: Int,
	)
	override fun applies(manifest: JsonObject): Boolean {
		val category = "forbidImplicitLambdaIt"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> = buildSet {
		val category = "forbidImplicitLambdaIt"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return@buildSet
		val parametersObj = catObj["parameters"]?.jsonObject ?: return@buildSet
		val messagesObj = catObj["messages"]?.jsonObject ?: return@buildSet
		val sourceRootsPerStack = parametersObj["sourceRootsPerStack"]?.jsonObject ?: return@buildSet
		val extensionsPerStack = parametersObj["extensionsPerStack"]?.jsonObject ?: return@buildSet
		val kotlinDirs = HarnessCheck.Companion.stringArrayFrom(sourceRootsPerStack, "kotlin")
		val kotlinExts = HarnessCheck.Companion.stringArrayFrom(extensionsPerStack, "kotlin")

		val results = psiResults?.implicitLambdaIt ?: emptyList()

		return buildSet<Finding> {
			kotlinDirs.forEach { dirPattern ->
				val dir = root / dirPattern
				if (!dir.exists()) {
					return@forEach
				}

				val (files, _) = HarnessCheck.Companion.walkSafe(root, dir)
				files.filter { file ->
					val ext = file.extension
					ext in kotlinExts
				}.forEach { file ->
					results.filter { it.file == file.name }.forEach { hit ->
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "${file.relativeTo(root)}:${hit.line}: implicit lambda parameter 'it' is forbidden"
						add(Finding(severity, category, msg))
					}
				}
			}
		}.toList()
	}
}
