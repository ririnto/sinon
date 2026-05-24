package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.HarnessCheck
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
object HookShebangRule : HarnessCheckRule() {
    override val category: String = "hookShebang"
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
                val expectedShebang = HarnessCheck.stringFrom(parametersObj, "expectedShebang")
                hooks
                    .filter { hookPath ->
                        val hook = root / hookPath
                        when {
                            !hook.isRegularFile() -> {
                                false
                            }

                            else -> {
                                val first = hook.readLines().firstOrNull() ?: ""
                                first != expectedShebang
                            }
                        }
                    }.map { hookPath ->
                        Finding(
                            HarnessCheck.severityOf(manifest, category),
                            category,
                            HarnessCheck.stringFrom(messagesObj, "default").takeIf { message ->
                                message.isNotEmpty()
                            }
                                ?: "$hookPath must start with $expectedShebang",
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
