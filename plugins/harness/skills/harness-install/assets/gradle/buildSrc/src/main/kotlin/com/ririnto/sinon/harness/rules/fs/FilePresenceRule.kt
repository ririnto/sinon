package com.ririnto.sinon.harness.rules.fs

import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.core.Severity
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlinx.serialization.json.JsonObject

/**
 * Rule that requires specified files to exist.
 *
 * @category
 *   filePresence
 */
object FilePresenceRule : HarnessCheckRule() {
    /**
     * Category identifier for this rule.
     */
    override val category: String = "filePresence"

    override fun validate(ctx: RuleContext): Collection<Finding> = buildList {
        val catObj = ctx.manifest.categoryObject(category)
        val parametersObj = catObj?.get("parameters") as? JsonObject
        if (catObj != null && parametersObj != null) {
            val paths = ctx.manifest.stringArray(category, "paths")
            for (path in paths) {
                val p = ctx.root / path
                when {
                    p.isSymbolicLink() && !ctx.isAllowedRootContractSymlink(p) ->
                        add(Finding(Severity.ERROR, category, "symlink file is not allowed: $path"))
                    !p.isRegularFile() ->
                        add(
                            Finding(
                                ctx.manifest.severityOf(category),
                                category,
                                "missing file: $path",
                            ),
                        )
                }
            }
        }
    }
}
