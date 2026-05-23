package ai.harness.gradle.rules

import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessPsiResults
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
object RequireFilesExistRule : HarnessCheckRule {
    override fun applies(manifest: JsonObject): Boolean {
        val category = "requireFilesExist"
        val catObj = manifest[category]?.jsonObject ?: return false
        val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
        return enabled
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults?,
    ): Collection<Finding> {
        val category = "requireFilesExist"
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        return if (catObj == null || parametersObj == null) {
            emptyList()
        } else {
            val paths = HarnessCheck.stringArrayFrom(parametersObj, "paths")
            paths
                .filter { path ->
                    val p = root / path
                    (p.isSymbolicLink() && !HarnessCheck.isAllowedRootContractSymlink(root, p)) || !p.isRegularFile()
                }.map { path ->
                    val p = root / path
                    when {
                        p.isSymbolicLink() &&
                            !HarnessCheck.isAllowedRootContractSymlink(
                                root,
                                p,
                            )
                        -> {
                            Finding(Severity.ERROR, category, "symlink file is not allowed: $path")
                        }

                        !p.isRegularFile() -> {
                            Finding(
                                HarnessCheck.severityOf(manifest, category),
                                category,
                                "missing file: $path",
                            )
                        }

                        else -> {
                            null
                        }
                    }
                }.filterNotNull()
        }
    }
}
