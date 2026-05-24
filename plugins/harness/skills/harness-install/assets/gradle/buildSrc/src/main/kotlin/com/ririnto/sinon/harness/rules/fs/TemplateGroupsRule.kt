package com.ririnto.sinon.harness.rules.fs

import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.Severity
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
object TemplateGroupsRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "templateGroups"

    override fun validate(ctx: RuleContext): Collection<Finding> = buildList {
        val catObj = ctx.manifest.categoryObject(category)
        val parametersObj = catObj?.get("parameters")?.jsonObject
        if (catObj != null && parametersObj != null) {
            for (group in ctx.manifest.stringArray(category, "groups")) {
                val p = ctx.root / JsonAccess.stringFromObject(parametersObj, "targetRoot") / group
                when {
                    p.isSymbolicLink() -> {
                        add(
                            Finding(
                                Severity.ERROR,
                                category,
                                "symlink directory is not allowed: ${JsonAccess.stringFromObject(parametersObj, "targetRoot")}/$group",
                            ),
                        )
                    }
                    !p.isDirectory() -> {
                        add(Finding(ctx.manifest.severityOf(category), category, "missing template group: ${JsonAccess.stringFromObject(parametersObj, "targetRoot")}/$group"))
                    }
                }
            }
        }
    }
}
