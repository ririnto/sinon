package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * Rule that requires executable scripts under certain directories to use /usr/bin/env shebang.
 */
class RequireEnvShebangUnderRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireEnvShebangUnder"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireEnvShebangUnder"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return emptyList()
		val parametersObj = catObj["parameters"]?.jsonObject ?: return emptyList()
		val messagesObj = catObj["messages"]?.jsonObject ?: return emptyList()
		val directories = HarnessCheck.Companion.stringArrayFrom(parametersObj, "directories")
		val expectedPrefix = HarnessCheck.Companion.stringFrom(parametersObj, "expectedPrefix")

		return buildSet<Finding> {
			directories.forEach { dirPath ->
				val dir = root / dirPath
				if (!dir.isDirectory()) {
					return@forEach
				}

				val (files, _) = HarnessCheck.Companion.walkSafe(root, dir)
				files.filter { it.isExecutable() }.forEach { file ->
					val firstLine = file.readText().lineSequence().firstOrNull() ?: ""
					if (firstLine.startsWith("#!") && !firstLine.startsWith(expectedPrefix)) {
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "executable script should use /usr/bin/env shebang: ${file.relativeTo(root)}"
						add(Finding(severity, category, msg))
					}
				}
			}
		}.toList()
	}
}
