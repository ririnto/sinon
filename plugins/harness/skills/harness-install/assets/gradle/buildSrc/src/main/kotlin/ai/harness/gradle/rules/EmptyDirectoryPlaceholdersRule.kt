package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.HarnessCheck
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Rule that requires .gitkeep or real files in empty directories.
 */
object EmptyDirectoryPlaceholdersRule : HarnessCheckRule() {
    override val category: String = "emptyDirectoryPlaceholders"
    override fun applies(manifest: JsonObject): Boolean =
        manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> {
        val severity = HarnessCheck.severityOf(manifest, category)
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        return when {
            catObj == null || parametersObj == null -> {
                emptyList()
            }

            else -> {
                val directories = HarnessCheck.stringArrayFrom(parametersObj, "directories")
                directories
                    .filter { dirPath ->
                        val dir = root / dirPath
                        when {
                            !dir.isDirectory() -> {
                                false
                            }

                            else -> {
                                /**
                                 * Directory enumeration may fail; return empty on silent fallback to treat as containing no real files.
                                 */
                                val realFiles =
                                    try {
                                        dir.listDirectoryEntries().filter { entry -> entry.name != ".gitkeep" }
                                    } catch (e: Exception) {
                                        emptyList()
                                    }
                                realFiles.isEmpty() && !(root / "$dirPath/.gitkeep").exists()
                            }
                        }
                    }.map { dirPath ->
                        Finding(
                            severity,
                            category,
                            "empty directory must keep placeholder or real files: $dirPath",
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
