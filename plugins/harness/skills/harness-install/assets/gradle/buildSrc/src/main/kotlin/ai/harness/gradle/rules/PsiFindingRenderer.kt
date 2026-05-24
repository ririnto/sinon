package ai.harness.gradle.rules

import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.PsiFinding
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Renders PSI findings into final harness findings.
 */
object PsiFindingRenderer {
    /** Render each raw PSI finding with manifest severity and message templates. */
    fun renderEach(
        findings: List<PsiFinding>,
        manifest: JsonObject,
    ): List<Finding> = findings.map { finding -> render(finding, manifest) }

    /** Render one raw PSI finding with manifest severity and message templates. */
    fun render(
        finding: PsiFinding,
        manifest: JsonObject,
    ): Finding {
        val category = finding.rule
        return Finding(
            HarnessCheck.severityOf(manifest, category),
            category,
            renderedMessage(finding, manifest),
        )
    }

    private fun renderedMessage(
        finding: PsiFinding,
        manifest: JsonObject,
    ): String {
        val category = finding.rule
        val messages = manifest[category]?.jsonObject?.get("messages")?.jsonObject
        val template = HarnessCheck.stringFrom(messages, "default")
        val rendered =
            template
                .replace("{file}", finding.file)
                .replace("{line}", finding.line.toString())
                .replace("{function}", finding.detail("function"))
                .replace("{name}", finding.detail("name"))
                .replace("{snippet}", finding.detail("name"))
                .replace("{imported}", finding.detail("imported"))
                .replace("{className}", finding.detail("className"))
                .replace("{memberName}", finding.detail("memberName"))
                .replace("{memberOverrideState}", finding.detail("memberOverrideState"))
                .replace("{memberVisibility}", finding.detail("memberVisibility"))
                .replace("{memberKind}", finding.detail("memberKind"))
                .replace("{context}", finding.detail("context"))
                .replace(
                    "{position}",
                    manifest[category]
                        ?.jsonObject
                        ?.get("parameters")
                        ?.jsonObject
                        ?.get("position")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?: "top",
                )
        return rendered.takeIf { message -> message.isNotEmpty() } ?: defaultSourceMessage(finding)
    }

    private fun defaultSourceMessage(finding: PsiFinding): String =
        when (finding.rule) {
            "leafFunctionBlankLines" -> {
                "${finding.file}:${finding.line}: leaf function `${finding.detail("function")}` " +
                    "contains too many blank lines; remove or extract the section"
            }
            "mutableCollection" -> {
                "${finding.file}:${finding.line}: mutable collection `${finding.detail("name")}` is forbidden; " +
                    "use immutable alternatives"
            }
            "classMemberOrdering" -> {
                "${finding.file}:${finding.line}: class `${finding.detail("className")}` member " +
                    "`${finding.detail("memberName")}` (${finding.detail("memberOverrideState")}:${finding.detail("memberVisibility")}:${finding.detail("memberKind")}) is out of order"
            }
            "companionObjectPosition" -> {
                "${finding.file}:${finding.line}: companion object in class `${finding.detail("className")}` must be repositioned"
            }
            "terminalBranchWhen" -> {
                "${finding.file}:${finding.line}: terminal if expression in ${finding.detail("context")}; use when instead"
            }
            else -> "${finding.file}:${finding.line}: ${finding.rule} violation"
        }
}
