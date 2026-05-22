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
 * Rule that forbids wildcard imports in Kotlin code.
 */
object ForbidWildcardImportRule : HarnessCheckRule {
	/**
	 * PSI detection result for a wildcard import.
	 */
	@Serializable
	data class Result(
		val file: String,
		val line: Int,
		val imported: String,
	)
	override fun applies(manifest: JsonObject): Boolean {
		val category = "forbidWildcardImport"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "forbidWildcardImport"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return emptyList()
		val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
		val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
		val sourceRootsPerStack = parametersObj["sourceRootsPerStack"]?.jsonObject ?: return emptyList()
		val extensionsPerStack = parametersObj["extensionsPerStack"]?.jsonObject ?: return emptyList()
		val kotlinDirs = HarnessCheck.Companion.stringArrayFrom(sourceRootsPerStack, "kotlin")
		val kotlinExts = HarnessCheck.Companion.stringArrayFrom(extensionsPerStack, "kotlin")
		val results = psiResults?.wildcardImports ?: emptyList()
		return kotlinDirs.flatMap { dirPattern ->
			val dir = root / dirPattern
			if (!dir.exists()) {
				emptyList()
			} else {
				val (files, _) = HarnessCheck.Companion.walkSafe(root, dir)
				files.filter { file ->
					file.extension in kotlinExts
				}.flatMap { file ->
					results.filter { it.file == file.name }.map { hit ->
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "${file.relativeTo(root)}:${hit.line}: wildcard import of `${hit.imported}` is forbidden; use explicit imports"
						Finding(severity, category, msg)
					}
				}
			}
		}
	}
}
