package com.ririnto.sinon.harness.rules.fs

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.HarnessCheck
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.core.Severity
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink

/**
 * Rule that requires specified directories to exist.
 */
object DirectoryPresenceRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "directoryPresence"

    override fun validate(ctx: RuleContext): Collection<Finding> {
        val catObj = ctx.manifest.categoryObject(category) ?: return emptyList()
        catObj.get("parameters")?.jsonObject ?: return emptyList()
        return buildList {
            ctx.manifest.stringArray(category, "paths").forEach { path ->
                val p = ctx.root / path
                when {
                    p.isSymbolicLink() -> add(Finding(Severity.ERROR, category, "symlink directory is not allowed: $path"))
                    !p.isDirectory() -> add(Finding(ctx.manifest.severityOf(category), category, "missing directory: $path"))
                }
            }
        }
    }
}
