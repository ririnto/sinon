package ai.harness.gradle

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

/**
 * Rule that requires agents to have proper frontmatter.
 */
class RequireAgentFrontmatterRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireAgentFrontmatter"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): List<Finding> {
		val category = "requireAgentFrontmatter"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return emptyList()
		val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
		val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
		val directory = HarnessCheck.Companion.stringFrom(parametersObj, "directory")
		val dirPath = root / directory

		if (!dirPath.isDirectory()) {
			val msg = HarnessCheck.Companion.stringFrom(messagesObj, "missingDirectory").takeIf { it.isNotEmpty() } ?: ".claude/agents must contain at least one .md agent"
			return listOf(Finding(severity, category, msg))
		}

		val files = try {
			dirPath.listDirectoryEntries().filter { it.isRegularFile() && it.extension == "md" }
		} catch (_: Exception) {
			emptyList()
		}
		if (files.isEmpty()) {
			val msg = HarnessCheck.Companion.stringFrom(messagesObj, "missingAgent").takeIf { it.isNotEmpty() } ?: ".claude/agents must contain at least one .md agent"
			return listOf(Finding(severity, category, msg))
		}

		return buildSet<Finding> {
			files.forEach { file ->
				val text = file.readText()
				if (!text.startsWith("---")) {
					val msg = HarnessCheck.Companion.stringFrom(messagesObj, "missingFrontmatter").takeIf { it.isNotEmpty() } ?: "agent missing frontmatter: ${file.relativeTo(root)}"
					add(Finding(severity, category, msg))
				} else {
					if (!"""(?m)^name:\s*[-a-z0-9]+\s*$""".toRegex().containsMatchIn(text)) {
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "missingName").takeIf { it.isNotEmpty() } ?: "agent missing name: ${file.relativeTo(root)}"
						add(Finding(severity, category, msg))
					}
					if (!"""(?m)^description:\s*.+$""".toRegex().containsMatchIn(text)) {
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "missingDescription").takeIf { it.isNotEmpty() } ?: "agent missing description: ${file.relativeTo(root)}"
						add(Finding(severity, category, msg))
					}
				}
			}
		}.toList()
	}
}
