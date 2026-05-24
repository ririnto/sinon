package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.HarnessCheck
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
object HookExecutableRule : HarnessCheckRule() {
    override val category: String = "hookExecutable"
    override fun applies(manifest: JsonObject): Boolean =
        manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> {
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        return when {
            catObj == null || parametersObj == null || messagesObj == null -> {
                emptyList()
            }

            else -> {
                val hooks = HarnessCheck.stringArrayFrom(parametersObj, "hooks")
                hooks
                    .filter { hookPath ->
                        val hook = root / hookPath
                        hook.isRegularFile() && !hook.isExecutable()
                    }.map { hookPath ->
                        Finding(
                            HarnessCheck.severityOf(manifest, category),
                            category,
                            HarnessCheck.stringFrom(messagesObj, "default").takeIf { message ->
                                message.isNotEmpty()
                            }
                                ?: "$hookPath must be executable",
                        )
                    }
            }
        }
    }

    override fun renderPsiFindings(
        findings: List<ai.harness.gradle.PsiFinding>,
        manifest: JsonObject,
    ): Collection<Finding> = emptyList()

    override fun findPsiFindings(
        file: Path,
        root: Path,
        psiFactory: org.jetbrains.kotlin.psi.KtPsiFactory?,
    ): List<ai.harness.gradle.PsiFinding> = emptyList()
}
