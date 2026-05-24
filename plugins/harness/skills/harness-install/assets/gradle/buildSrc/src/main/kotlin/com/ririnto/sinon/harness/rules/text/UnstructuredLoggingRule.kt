package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Rule that delegates unstructured-logging findings to source AST/PSI analysis.
 */
object UnstructuredLoggingRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "unstructuredLogging"

    override fun applies(ctx: RuleContext): Boolean {
        return ctx.manifest.categoryObject(category)?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true
    }

    override fun validate(ctx: RuleContext): Collection<Finding> = emptyList()
}
