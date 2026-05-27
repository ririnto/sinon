package com.ririnto.sinon.harness.ast

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.HarnessCheck
import com.ririnto.sinon.harness.core.JsonAccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Renders AST findings into final harness findings.
 */
object AstFindingRenderer {
    /**
     * Render each raw AST finding with manifest severity and message templates.
     */
    fun renderEach(
        findings: List<AstFinding>,
        manifest: JsonObject,
    ): List<Finding> = findings.map { finding -> render(finding, manifest) }

    /**
     * Render one raw AST finding with manifest severity and message templates.
     */
    fun render(
        finding: AstFinding,
        manifest: JsonObject,
    ): Finding {
        val category = finding.rule
        return Finding(
            severity = HarnessCheck.severityOf(manifest, category),
            category = category,
            message = renderedMessage(finding, manifest),
            file = finding.file,
            startLine = finding.line,
            startColumn = 1,
        )
    }

    private fun renderedMessage(
        finding: AstFinding,
        manifest: JsonObject,
    ): String {
        val categoryObj = manifest[finding.rule]?.jsonObject ?: error("manifest missing category ${finding.rule}")
        val position =
            categoryObj
                .get("parameters")
                ?.jsonObject
                ?.get("position")
                ?.jsonPrimitive
                ?.contentOrNull ?: "top"
        val message = JsonAccess.stringFromObject(categoryObj.get("messages")?.jsonObject, "default")
        return message
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
            .replace("{position}", position)
            .replace(Regex("^\\S+:\\d+:\\s"), "")
            .takeIf { msg -> msg.isNotEmpty() }
            ?: error("manifest messages.default missing for category ${finding.rule}")
    }
}
