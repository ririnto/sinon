package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.relativeTo

/**
 * Rule that forbids disallowed symlinks at the root level.
 */
class ForbidUnsafeSymlinksRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "forbidUnsafeSymlinks"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "forbidUnsafeSymlinks"
		val catObj = manifest[category]?.jsonObject ?: return emptyList()
		val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
		val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
		val allowedPairs = parametersObj["allowedSymlinkPairs"]?.jsonArray?.mapNotNull { pairElem ->
			val pair = pairElem.jsonArray
			if (pair.size < 2) null else {
				val a = pair[0].jsonPrimitive.contentOrNull ?: return@mapNotNull null
				val b = pair[1].jsonPrimitive.contentOrNull ?: return@mapNotNull null
				a to b
			}
		} ?: emptyList()

		val allowed = buildSet<Pair<String, String>> {
			allowedPairs.forEach { (a, b) ->
				add(a to b)
				add(b to a)
			}
		}

		return buildSet<Finding> {
			val rootFiles = try {
				root.listDirectoryEntries()
			} catch (_: Exception) {
				emptyList()
			}
			rootFiles.filter { it.isSymbolicLink() }.forEach { file ->
				val target = try {
					file.readSymbolicLink().toString()
				} catch (_: Exception) {
					""
				}
				if (file.name to target !in allowed) {
					val msg = HarnessCheck.Companion.stringFrom(messagesObj, "fileNotAllowed").takeIf { it.isNotEmpty() } ?: "symlink file is not allowed: ${file.relativeTo(root)}"
					add(Finding(Severity.ERROR, category, msg))
				}
			}
		}.toList()
	}
}
