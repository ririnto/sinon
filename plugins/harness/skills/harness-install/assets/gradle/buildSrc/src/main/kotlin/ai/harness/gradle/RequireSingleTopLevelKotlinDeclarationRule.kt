package ai.harness.gradle

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.relativeTo

/**
 * Rule that requires Kotlin files to have exactly one top-level declaration of allowed type.
 */
class RequireSingleTopLevelKotlinDeclarationRule : HarnessCheckRule {
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

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> = buildSet {
		val category = "requireSingleTopLevelKotlinDeclaration"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return@buildSet
		val parametersObj = catObj["parameters"]?.jsonObject ?: return@buildSet
		val messagesObj = catObj["messages"]?.jsonObject ?: return@buildSet
		val sourceRootsPerStack = parametersObj["sourceRootsPerStack"]?.jsonObject ?: return@buildSet
		val extensionsPerStack = parametersObj["extensionsPerStack"]?.jsonObject ?: return@buildSet
		val kotlinDirs = HarnessCheck.Companion.stringArrayFrom(sourceRootsPerStack, "kotlin")
		val kotlinExts = HarnessCheck.Companion.stringArrayFrom(extensionsPerStack, "kotlin")
		val allowedDeclarations = HarnessCheck.Companion.stringArrayFrom(parametersObj, "allowedDeclarations")

		val results = psiResults?.topLevelDeclarations ?: emptyList()

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
					val info = results.find { it.file == file.name }
					if (info != null) {
						when {
							info.count != 1 -> {
								val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "${file.relativeTo(root)}: file must have single top-level declaration, found ${info.count}"
								add(Finding(severity, category, msg))
							}
							else -> {
								val declType = info.firstKind
								if (declType != "unknown" && declType !in allowedDeclarations) {
									val msg = HarnessCheck.Companion.stringFrom(messagesObj, "default").takeIf { it.isNotEmpty() } ?: "${file.relativeTo(root)}: top-level $declType is not allowed"
									add(Finding(severity, category, msg))
								}
							}
						}
					}
				}
			}
		}.toList()
	}
}
