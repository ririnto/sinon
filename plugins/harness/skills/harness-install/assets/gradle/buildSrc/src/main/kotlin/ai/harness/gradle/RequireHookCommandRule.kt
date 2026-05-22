package ai.harness.gradle

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * Rule that requires hooks to declare and run validation commands.
 */
class RequireHookCommandRule : HarnessCheckRule {
	override fun applies(manifest: JsonObject): Boolean {
		val category = "requireHookCommand"
		val catObj = manifest[category]?.jsonObject ?: return false
		val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
		return enabled
	}

	override fun validate(manifest: JsonObject, root: Path, psiResults: HarnessPsiResults?): Collection<Finding> = buildSet {
		val category = "requireHookCommand"
		val severity = HarnessCheck.Companion.severityOf(manifest, category)
		val catObj = manifest[category]?.jsonObject ?: return@buildSet
		val parametersObj = catObj["parameters"]?.jsonObject ?: return@buildSet
		val messagesObj = catObj["messages"]?.jsonObject ?: return@buildSet
		val allowedCmdObj = parametersObj["allowedCommands"]?.jsonObject
		val allowedCmds = HarnessCheck.Companion.stringArrayFrom(allowedCmdObj, "gradle")
		val allowedPreCommitCmdObj = parametersObj["allowedPreCommitCommands"]?.jsonObject
		val allowedPreCommitCmds = HarnessCheck.Companion.stringArrayFrom(allowedPreCommitCmdObj, "gradle")
		val prePushPath = HarnessCheck.Companion.stringFrom(parametersObj, "prePushHook")
		val preCommitPath = HarnessCheck.Companion.stringFrom(parametersObj, "preCommitHook")

		return buildSet<Finding> {
			val prePushHook = root / prePushPath
			if (prePushHook.isRegularFile()) {
				val text = prePushHook.readText()
				val command = text.lineSequence().firstOrNull { it.startsWith("# Harness validation command: ") }?.removePrefix("# Harness validation command: ")?.trim() ?: ""
				when {
					command.isEmpty() -> {
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "missingDeclaration").takeIf { it.isNotEmpty() } ?: "pre-push hook must declare Harness validation command"
						add(Finding(severity, category, msg))
					}
					command !in allowedCmds -> {
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "unsupportedCommand").takeIf { it.isNotEmpty() } ?: "pre-push hook declares unsupported validation command: $command"
						add(Finding(severity, category, msg))
					}
					!text.contains(command) -> {
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "commandNotRun").takeIf { it.isNotEmpty() } ?: "pre-push hook must run the declared validation command"
						add(Finding(severity, category, msg))
					}
				}
			}

			val preCommitHook = root / preCommitPath
			if (preCommitHook.isRegularFile()) {
				val text = preCommitHook.readText()
				val command = text.lineSequence().firstOrNull { it.startsWith("# Harness validation command: ") }?.removePrefix("# Harness validation command: ")?.trim() ?: ""
				when {
					command.isEmpty() && allowedPreCommitCmds.isNotEmpty() -> {
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "missingDeclaration").takeIf { it.isNotEmpty() } ?: "pre-commit hook must declare validation command"
						add(Finding(severity, category, msg))
					}
					command.isNotEmpty() && command !in allowedPreCommitCmds -> {
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "unsupportedCommand").takeIf { it.isNotEmpty() } ?: "pre-commit hook declares unsupported validation command: $command"
						add(Finding(severity, category, msg))
					}
					command.isNotEmpty() && !text.contains(command) -> {
						val msg = HarnessCheck.Companion.stringFrom(messagesObj, "commandNotRun").takeIf { it.isNotEmpty() } ?: "pre-commit hook must run the declared validation command"
						add(Finding(severity, category, msg))
					}
				}
			}
		}.toList()
	}
}
