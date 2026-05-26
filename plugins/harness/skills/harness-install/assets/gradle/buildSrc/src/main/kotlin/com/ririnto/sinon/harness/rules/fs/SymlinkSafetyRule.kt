package com.ririnto.sinon.harness.rules.fs

import com.ririnto.sinon.harness.ast.AstFinding
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.HarnessCheck
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.core.Severity
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.relativeTo

/**
 * Rule that forbids disallowed symlinks at the root level.
 *
 * Validates that all symbolic links in the manifest root directory match
 * the configured allowed symlink pairs. Rejects any symlinks not explicitly
 * permitted in the allowedSymlinkPairs configuration.
 *
 * @category
 *   symlinkSafety
 */
object SymlinkSafetyRule : HarnessCheckRule() {
    /**
     * Category identifier for this rule.
     */
    override val category: String = "symlinkSafety"

    override fun validate(ctx: RuleContext): Collection<Finding> =
        buildList {
            val catObj = ctx.manifest.categoryObject(category)
            val parametersObj = catObj?.get("parameters")?.jsonObject
            if (catObj != null && parametersObj != null) {
                val allowedPairs =
                    parametersObj["allowedSymlinkPairs"]
                        ?.jsonArray
                        ?.mapNotNull { pairElem ->
                            val pair = pairElem.jsonArray
                            when {
                                2 <= pair.size && pair[0].jsonPrimitive.contentOrNull != null &&
                                    pair[1].jsonPrimitive.contentOrNull != null -> {
                                    pair[0].jsonPrimitive.contentOrNull to pair[1].jsonPrimitive.contentOrNull
                                }

                                else -> {
                                    null
                                }
                            }
                        } ?: emptyList()
                ctx.root
                    .listDirectoryEntries()
                    .filter { file -> file.isSymbolicLink() }
                    .filter { file ->
                        (file.name to file.readSymbolicLink().pathString) !in
                            (allowedPairs.flatMap { (a, b) -> listOf(a to b, b to a) }).toSet()
                    }.map { file ->
                        Finding(
                            Severity.ERROR,
                            category,
                            ctx.manifest.stringValue(category, "fileNotAllowed").takeIf { message ->
                                message.isNotEmpty()
                            }
                                ?: "symlink file is not allowed: ${file.relativeTo(ctx.root)}",
                        )
                    }.forEach { finding -> add(finding) }
            }
        }
}
