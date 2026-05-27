package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.div
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

/**
 * Rule that requires hooks to be executable.
 *
 * Operates on file metadata (executable bit); no text parsing or AST parser required.
 */
object HookExecutableRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "hookExecutable"

    override fun validate(ctx: RuleContext): Collection<Finding> {
        if (ctx.manifest.categoryObject(category) == null) {
            return emptyList()
        }
        return ctx.manifest
            .stringArray(category, "hooks")
            .filter { hookPath -> (ctx.root / hookPath).isRegularFile() }
            .filter { hookPath -> !(ctx.root / hookPath).isExecutable() }
            .map { hookPath ->
                Finding(
                    ctx.manifest.severityOf(category),
                    category,
                    ctx.manifest.stringValue(category, "default").takeIf { defaultMsg -> defaultMsg.isNotEmpty() }
                        ?: "$hookPath must be executable",
                )
            }
    }

    override fun format(ctx: RuleContext): Collection<Path> {
        if (ctx.manifest.categoryObject(category) == null) {
            return emptyList()
        }
        return ctx.manifest
            .stringArray(category, "hooks")
            .filter { hookPath -> (ctx.root / hookPath).isRegularFile() }
            .filter { hookPath -> !(ctx.root / hookPath).isExecutable() }
            .mapNotNull { hookPath ->
                val fullPath = ctx.root / hookPath
                val currentPerms = Files.getPosixFilePermissions(fullPath)
                val newPerms = currentPerms + setOf(
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_EXECUTE
                )
                Files.setPosixFilePermissions(fullPath, newPerms)
                fullPath
            }
    }
}
