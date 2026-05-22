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
 * Rule that requires Kotlin files to have exactly one top-level declaration of allowed type.
 */
object RequireSingleTopLevelKotlinDeclarationRule : HarnessCheckRule {
	/**
	 * PSI analysis result for top-level declarations in a Kotlin source file.
	 */
	@Serializable
	data class Result(
		val file: String,
		val count: Int,
		val firstKind: String,
	)
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireSingleTopLevelKotlinDeclaration"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> {
		val category = "requireSingleTopLevelKotlinDeclaration"
		val severity = HarnessCheck.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject
		val parametersObj = catObj?.get("parameters")?.jsonObject
		val messagesObj = catObj?.get("messages")?.jsonObject
		val sourceRootsPerStack = parametersObj?.get("sourceRootsPerStack")?.jsonObject
		val extensionsPerStack = parametersObj?.get("extensionsPerStack")?.jsonObject
		return if (catObj == null || parametersObj == null || messagesObj == null || sourceRootsPerStack == null || extensionsPerStack == null) {
			emptyList()
		} else {
			val kotlinDirs = HarnessCheck.stringArrayFrom(sourceRootsPerStack, "kotlin")
			val kotlinExts = HarnessCheck.stringArrayFrom(extensionsPerStack, "kotlin")
			val allowedDeclarations = HarnessCheck.stringArrayFrom(parametersObj, "allowedDeclarations")
			val results = psiResults?.topLevelDeclarations ?: emptyList()
			kotlinDirs.flatMap { dirPattern ->
				val dir = root / dirPattern
				if (!dir.exists()) {
					emptyList()
				} else {
					val (files, _) = HarnessCheck.walkSafe(root, dir)
					files.filter { file ->
						file.extension in kotlinExts
					}.mapNotNull { file ->
						val info = results.find { it.file == file.name }
						when {
							info == null -> null
							info.count != 1 -> {
								val msg = HarnessCheck.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "${file.relativeTo(root)}: file must have single top-level declaration, found ${info.count}"
								Finding(severity, category, msg)
							}
							else -> {
								val declType = info.firstKind
								if (declType != "unknown" && declType !in allowedDeclarations) {
									val msg = HarnessCheck.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "${file.relativeTo(root)}: top-level $declType is not allowed"
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
	}
}
