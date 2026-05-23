package ai.harness.gradle.rules

import ai.harness.gradle.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessPsiResults
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path

/**
 * Rule that requires documentation files to contain specified content.
 */
object RequireDocContentRule : HarnessCheckRule {
    override fun applies(manifest: JsonObject): Boolean {
        val category = "requireDocContent"
        val catObj = manifest[category]?.jsonObject ?: return false
        val enabled = catObj["enabled"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
        return enabled
    }

    override fun validate(
        manifest: JsonObject,
        root: Path,
        psiResults: HarnessPsiResults?,
    ): Collection<Finding> {
        val category = "requireDocContent"
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val checks = parametersObj?.get("checks")?.jsonArray
        return if (catObj == null || parametersObj == null || checks == null) {
            emptyList()
        } else {
            checks
                .filter { checkElem ->
                    val checkObj = checkElem.jsonObject
                    val files = HarnessCheck.stringArrayFrom(checkObj, "files")
                    val containsAll = HarnessCheck.stringArrayFrom(checkObj, "containsAll")
                    val content = files.map { filePath -> HarnessCheck.readSafe(root, filePath) }.joinToString("\n")
                    !containsAll.all { contentStr -> content.contains(contentStr) }
                }.map { checkElem ->
                    val checkObj = checkElem.jsonObject
                    Finding(
                        HarnessCheck.severityOf(manifest, category),
                        category,
                        HarnessCheck.stringFrom(checkObj, "failureMessage"),
                    )
                }
        }
    }
}
