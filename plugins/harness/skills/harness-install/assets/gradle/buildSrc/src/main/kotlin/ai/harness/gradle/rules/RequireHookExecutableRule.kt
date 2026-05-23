package ai.harness.gradle.rules

import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessPsiResults
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

/**
 * Rule that requires hooks to be executable.
 */
object RequireHookExecutableRule : HarnessCheckRule {
    override fun applies(manifest: JsonObject): Boolean {
        val category = "requireHookExecutable"
        val catObj = manifest[category]?.jsonObject ?: return false
        val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
        return enabled
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults?,
    ): Collection<Finding> {
        val category = "requireHookExecutable"
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        return if (catObj == null || parametersObj == null || messagesObj == null) {
            emptyList()
        } else {
            val hooks = HarnessCheck.stringArrayFrom(parametersObj, "hooks")
            hooks
                .filter { hookPath ->
                    val hook = root / hookPath
                    hook.isRegularFile() && !hook.isExecutable()
                }.map { hookPath ->
                    Finding(
                        HarnessCheck.severityOf(manifest, category),
                        category,
                        HarnessCheck.stringFrom(messagesObj, "default").takeIf { message -> message.isNotEmpty() }
                            ?: "$hookPath must be executable",
                    )
                }
        }
    }
}
