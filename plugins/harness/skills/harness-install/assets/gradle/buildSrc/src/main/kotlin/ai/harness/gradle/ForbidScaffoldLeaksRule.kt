package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import java.io.File

/**
 * Rule that forbids scaffold placeholder patterns in active assets.
 */
object ForbidScaffoldLeaksRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "forbidScaffoldLeaks"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> = buildSet {
		val category = "forbidScaffoldLeaks"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return@buildSet
		val parametersObj = catObj["parameters"]?.jsonObject ?: return@buildSet
		val messagesObj = catObj["messages"]?.jsonObject ?: return@buildSet
		val scopeObj = parametersObj["scope"]?.jsonObject ?: return@buildSet
		val bases = HarnessCheck.Companion.stringArrayFrom(scopeObj, "bases")
		val excludedSubtrees = HarnessCheck.Companion.stringArrayFrom(scopeObj, "excludedSubtrees")
		val extensions = HarnessCheck.Companion.stringArrayFrom(scopeObj, "extensions")
		val patterns = parametersObj["patterns"]?.jsonArray?.mapNotNull { patternElem ->
			val obj = patternElem.jsonObject
			val pattern = HarnessCheck.Companion.stringFrom(obj, "pattern")
			val label = HarnessCheck.Companion.stringFrom(obj, "label")
			if (pattern.isEmpty() || label.isEmpty()) null else pattern to label
		} ?: emptyList()

		val excludedPaths = excludedSubtrees.map { root / it }
		val regexes = patterns.mapNotNull { (pattern, label) ->
			try {
				pattern.toRegex() to label
			} catch (_: Exception) {
				null
			}
		}

		return buildSet<Finding> {
			bases.forEach { basePath ->
				val base = root / basePath
				val (files, _) = HarnessCheck.Companion.walkSafe(root, base)
				files.forEach { file ->
					val ext = file.extension
					if (ext in extensions && excludedPaths.none { file.toString().startsWith(it.toString()) }) {
						val text = file.readText()
						regexes.forEach { (regex, label) ->
							if (regex.containsMatchIn(text)) {
								val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "$label in active asset: ${file.relativeTo(root.toFile())}"
								add(Finding(severity, category, msg))
							}
						}
					}
				}
			}
		}.toList()
	}
}
