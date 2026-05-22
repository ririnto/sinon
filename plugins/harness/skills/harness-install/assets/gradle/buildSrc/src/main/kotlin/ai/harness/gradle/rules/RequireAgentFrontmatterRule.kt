package ai.harness.gradle.rules

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import java.io.File
import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessCheckRule
import ai.harness.gradle.HarnessPsiResults

/**
 * Rule that requires agents to have proper frontmatter.
 */
object RequireAgentFrontmatterRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireAgentFrontmatter"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireAgentFrontmatter"
		val severity = HarnessCheck.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject
		val parametersObj = catObj?.get("parameters")?.jsonObject
		val messagesObj = catObj?.get("messages")?.jsonObject
		return if (catObj == null || parametersObj == null || messagesObj == null) {
			emptyList()
		} else {
			val directory = HarnessCheck.stringFrom(parametersObj, "directory")
			val dirPath = root / directory
			if (!dirPath.isDirectory()) {
				val msg = HarnessCheck.stringFrom(messagesObj, "missingDirectory").takeIf { it.isNotEmpty() } ?: ".claude/agents must contain at least one .md agent"
				listOf(Finding(severity, category, msg))
			} else {
				val files = try {
					dirPath.listDirectoryEntries().filter { it.isRegularFile() && it.extension == "md" }
				} catch (_: Exception) {
					emptyList()
				}
				if (files.isEmpty()) {
					val msg = HarnessCheck.stringFrom(messagesObj, "missingAgent").takeIf { it.isNotEmpty() } ?: ".claude/agents must contain at least one .md agent"
					listOf(Finding(severity, category, msg))
				} else {
					files.flatMap { file ->
						val text = file.readText()
						if (!text.startsWith("---")) {
							val msg = HarnessCheck.stringFrom(messagesObj, "missingFrontmatter").takeIf { it.isNotEmpty() } ?: "agent missing frontmatter: ${file.relativeTo(root)}"
							listOf(Finding(severity, category, msg))
						} else {
							listOfNotNull(
								if (!"""(?m)^name:\s*[-a-z0-9]+\s*$""".toRegex().containsMatchIn(text)) {
									val msg = HarnessCheck.stringFrom(messagesObj, "missingName").takeIf { it.isNotEmpty() } ?: "agent missing name: ${file.relativeTo(root)}"
									Finding(severity, category, msg)
								} else null,
								if (!"""(?m)^description:\s*.+$""".toRegex().containsMatchIn(text)) {
									val msg = HarnessCheck.stringFrom(messagesObj, "missingDescription").takeIf { it.isNotEmpty() } ?: "agent missing description: ${file.relativeTo(root)}"
									Finding(severity, category, msg)
								} else null
							)
						}
					}
				}
			}
		}
	}
}
