package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.isExecutable
import kotlin.io.path.relativeTo
import kotlinx.serialization.json.jsonObject

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
        val parametersObj = (ctx.manifest.categoryObject(category) ?: return emptyList()).get("parameters")?.jsonObject
            ?: return emptyList()
        val hooks = JsonAccess.stringArrayFromObject(parametersObj, "hooks")
        return hooks
            .mapNotNull { hookPath -> HookPathSupport.safeHookPath(ctx.root, hookPath) }
            .filter { hook -> !hook.isExecutable() }
            .map { hook ->
                Finding(
                    ctx.manifest.severityOf(category),
                    category,
                    ctx.manifest.stringValue(category, "default").takeIf { defaultMsg -> defaultMsg.isNotEmpty() }
                        ?: "${hook.relativeTo(ctx.root)} must be executable",
                )
            }
    }

    override fun format(ctx: RuleContext): Collection<Path> {
        val parametersObj = (ctx.manifest.categoryObject(category) ?: return emptyList()).get("parameters")?.jsonObject
            ?: return emptyList()
        val hooks = JsonAccess.stringArrayFromObject(parametersObj, "hooks")
        return hooks
            .mapNotNull { hookPath -> HookPathSupport.safeHookPath(ctx.root, hookPath) }
            .filter { hook -> !hook.isExecutable() }
            .mapNotNull { hook ->
                val currentPerms = Files.getPosixFilePermissions(hook)
                val newPerms = currentPerms + setOf(
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_EXECUTE
                )
                Files.setPosixFilePermissions(hook, newPerms)
                hook
            }
    }
}
