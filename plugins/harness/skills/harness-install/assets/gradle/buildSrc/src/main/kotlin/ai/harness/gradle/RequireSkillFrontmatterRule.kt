package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * Rule that requires skills to have proper frontmatter.
 */
object RequireSkillFrontmatterRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireSkillFrontmatter"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> = buildSet {
		val category = "requireSkillFrontmatter"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return@buildSet
		val parametersObj = catObj["parameters"]?.jsonObject ?: return@buildSet
		val messagesObj = catObj["messages"]?.jsonObject ?: return@buildSet
		val rootDirectory = HarnessCheck.Companion.stringFrom(parametersObj, "rootDirectory")
		val filename = HarnessCheck.Companion.stringFrom(parametersObj, "filename")

		val dirPath = root / rootDirectory
		if (!dirPath.isDirectory()) {
			val msg = HarnessCheck.Companion.stringFrom(messagesObj, "missingDirectory").takeIf { it.isNotEmpty() } ?: ".claude/skills must contain at least one SKILL.md"
			return listOf(Finding(severity, category, msg))
		}

		val files = try {
			dirPath.walk().filter { it.isRegularFile() && it.name == filename }.toList()
		} catch (_: Exception) {
			emptyList()
		}
		if (files.isEmpty()) {
			val msg = HarnessCheck.Companion.stringFrom(messagesObj, "missingSkill").takeIf { it.isNotEmpty() } ?: ".claude/skills must contain at least one SKILL.md"
			return listOf(Finding(severity, category, msg))
		}

		return buildSet<Finding> {
			files.forEach { file ->
				val text = file.readText()
				if (!text.startsWith("---")) {
					val msg = HarnessCheck.Companion.stringFrom(messagesObj, "missingFrontmatter").takeIf { it.isNotEmpty() } ?: "skill missing frontmatter: ${file.relativeTo(root)}"
					add(Finding(severity, category, msg))
				} else {
					if (!"""(?m)^description:\s*.+$""".toRegex().containsMatchIn(text)) {
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "missingDescription").takeIf { it.isNotEmpty() } ?: "skill missing description: ${file.relativeTo(root)}"
						add(Finding(severity, category, msg))
					}
				}
			}
		}.toList()
	}
}
