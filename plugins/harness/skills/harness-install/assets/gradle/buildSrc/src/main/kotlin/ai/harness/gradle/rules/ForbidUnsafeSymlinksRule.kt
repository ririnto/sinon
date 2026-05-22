package ai.harness.gradle.rules

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.relativeTo
import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessCheckRule
import ai.harness.gradle.HarnessPsiResults
import ai.harness.gradle.Severity

/**
 * Rule that forbids disallowed symlinks at the root level.
 */
object ForbidUnsafeSymlinksRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "forbidUnsafeSymlinks"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "forbidUnsafeSymlinks"
		val catObj = manifest[category]?.jsonObject
		val parametersObj = catObj?.get("parameters")?.jsonObject
		val messagesObj = catObj?.get("messages")?.jsonObject
		return if (catObj == null || parametersObj == null || messagesObj == null) {
			emptyList()
		} else {
			val allowedPairs = parametersObj["allowedSymlinkPairs"]?.jsonArray?.mapNotNull { pairElem ->
				val pair = pairElem.jsonArray
				if (pair.size < 2) null else {
					val a = pair[0].jsonPrimitive.contentOrNull ?: return@mapNotNull null
					val b = pair[1].jsonPrimitive.contentOrNull ?: return@mapNotNull null
					a to b
				}
			} ?: emptyList()
			val allowed = (allowedPairs.flatMap { (a, b) -> listOf(a to b, b to a) }).toSet()
			val rootFiles = try {
				root.listDirectoryEntries()
			} catch (_: Exception) {
				emptyList()
			}
			rootFiles.filter { it.isSymbolicLink() }.mapNotNull { file ->
				if (file.name to (try { file.readSymbolicLink().toString() } catch (_: Exception) { "" }) !in allowed) {
					Finding(Severity.ERROR, category, HarnessCheck.Companion.stringFrom(messagesObj, "fileNotAllowed").takeIf { it.isNotEmpty() } ?: "symlink file is not allowed: ${file.relativeTo(root)}")
				} else {
					null
				}
			}
		}
	}
}
