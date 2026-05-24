package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.Severity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink

/**
 * Rule that requires specified files to exist.
 */
object FilePresenceRule : HarnessCheckRule() {
    override val category: String = "filePresence"
    override fun applies(manifest: JsonObject): Boolean =
        manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> {
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        return when {
            catObj == null || parametersObj == null -> {
                emptyList()
            }

            else -> {
                val paths = HarnessCheck.stringArrayFrom(parametersObj, "paths")
                buildList {
                    for (path in paths) {
                        val p = root / path
                        when {
                            p.isSymbolicLink() && !HarnessCheck.isAllowedRootContractSymlink(root, p) -> {
                                add(Finding(Severity.ERROR, category, "symlink file is not allowed: $path"))
                            }
                            !p.isRegularFile() -> {
                                add(
                                    Finding(
                                        HarnessCheck.severityOf(manifest, category),
                                        category,
                                        "missing file: $path",
                                    ),
                                )
                            }
                        }
                    }
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
