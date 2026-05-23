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
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink

/**
 * Rule that requires template groups to exist as directories.
 */
object RequireTemplateGroupsRule : HarnessCheckRule {
    override fun applies(manifest: JsonObject): Boolean {
        val category = "requireTemplateGroups"
        val catObj = manifest[category]?.jsonObject ?: return false
        val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
        return enabled
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults?,
    ): Collection<Finding> {
        val category = "requireTemplateGroups"
        val severity = HarnessCheck.severityOf(manifest, category)
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        return if (catObj == null || parametersObj == null) {
            emptyList()
        } else {
            val targetRoot = HarnessCheck.stringFrom(parametersObj, "targetRoot")
            val groups = HarnessCheck.stringArrayFrom(parametersObj, "groups")
            groups
                .filter { group ->
                    val p = root / "$targetRoot/$group"
                    p.isSymbolicLink() || !p.isDirectory()
                }.map { group ->
                    val p = root / "$targetRoot/$group"
                    when {
                        p.isSymbolicLink() -> {
                            Finding(
                                Severity.ERROR,
                                category,
                                "symlink directory is not allowed: $targetRoot/$group",
                            )
                        }

                        !p.isDirectory() -> {
                            Finding(severity, category, "missing template group: $targetRoot/$group")
                        }

                        else -> {
                            null
                        }
                    }
                }.filterNotNull()
        }
    }
}
