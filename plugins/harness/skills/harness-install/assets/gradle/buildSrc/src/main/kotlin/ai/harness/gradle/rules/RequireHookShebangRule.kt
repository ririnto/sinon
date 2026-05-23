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
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines

/**
 * Rule that requires hooks to have correct shebang.
 */
object RequireHookShebangRule : HarnessCheckRule {
    override fun applies(manifest: JsonObject): Boolean {
        val category = "requireHookShebang"
        val catObj = manifest[category]?.jsonObject ?: return false
        val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
        return enabled
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults?,
    ): Collection<Finding> {
        val category = "requireHookShebang"
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        return if (catObj == null || parametersObj == null || messagesObj == null) {
            emptyList()
        } else {
            val hooks = HarnessCheck.stringArrayFrom(parametersObj, "hooks")
            val expectedShebang = HarnessCheck.stringFrom(parametersObj, "expectedShebang")
            hooks
                .filter { hookPath ->
                    val hook = root / hookPath
                    if (!hook.isRegularFile()) {
                        false
                    } else {
                        val first = hook.readLines().firstOrNull() ?: ""
                        first != expectedShebang
                    }
                }.map { hookPath ->
                    Finding(
                        HarnessCheck.severityOf(manifest, category),
                        category,
                        HarnessCheck.stringFrom(messagesObj, "default").takeIf { message -> message.isNotEmpty() }
                            ?: "$hookPath must start with $expectedShebang",
                    )
                }
        }
    }
}
