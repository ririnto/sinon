package ai.harness.gradle.rules

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessCheckRule
import ai.harness.gradle.HarnessPsiResults

/**
 * Rule that requires executable scripts under certain directories to use /usr/bin/env shebang.
 */
object RequireEnvShebangUnderRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireEnvShebangUnder"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireEnvShebangUnder"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject
		val parametersObj = catObj?.get("parameters")?.jsonObject
		val messagesObj = catObj?.get("messages")?.jsonObject
		return if (catObj == null || parametersObj == null || messagesObj == null) {
			emptyList()
		} else {
			val directories = HarnessCheck.Companion.stringArrayFrom(parametersObj, "directories")
			val expectedPrefix = HarnessCheck.Companion.stringFrom(parametersObj, "expectedPrefix")
			directories.flatMap { dirPath ->
				val dir = root / dirPath
				if (!dir.isDirectory()) {
					emptyList()
				} else {
					val (files, _) = HarnessCheck.Companion.walkSafe(root, dir)
					files.filter { it.isExecutable() }.mapNotNull { file ->
						val firstLine = file.readText().lineSequence().firstOrNull() ?: ""
						if (firstLine.startsWith("#!") && !firstLine.startsWith(expectedPrefix)) {
							val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "executable script should use /usr/bin/env shebang: ${file.relativeTo(root)}"
							Finding(severity, category, msg)
						} else {
							null
						}
					}
				}
			}
		}
	}
}
