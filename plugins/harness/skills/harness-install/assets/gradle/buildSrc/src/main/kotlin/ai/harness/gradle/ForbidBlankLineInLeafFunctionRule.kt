package ai.harness.gradle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.relativeTo

/**
 * Rule that forbids blank lines in leaf function bodies.
 */
object ForbidBlankLineInLeafFunctionRule : HarnessCheckRule {
	/**
	 * PSI detection result for a single blank line in a leaf function.
	 */
	@Serializable
	data class Result(
		val file: String,
		val name: String,
		val line: Int,
	)
	override fun applies(manifest: JsonObject): Boolean {
		val category = "forbidBlankLineInLeafFunction"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> = buildSet {
		val category = "forbidBlankLineInLeafFunction"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return@buildSet
		val parametersObj = catObj["parameters"]?.jsonObject ?: return@buildSet
		val messagesObj = catObj["messages"]?.jsonObject ?: return@buildSet
		val sourceRootsPerStack = parametersObj["sourceRootsPerStack"]?.jsonObject ?: return@buildSet
		val extensionsPerStack = parametersObj["extensionsPerStack"]?.jsonObject ?: return@buildSet
		val kotlinDirs = HarnessCheck.Companion.stringArrayFrom(sourceRootsPerStack, "kotlin")
		val kotlinExts = HarnessCheck.Companion.stringArrayFrom(extensionsPerStack, "kotlin")

		val results = psiResults?.blankLinesInLeafFunctions ?: emptyList()

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
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "${file.relativeTo(root)}:${hit.line}: blank line in leaf function"
						add(Finding(severity, category, msg))
					}
				}
			}
		}.toList()
	}
}
