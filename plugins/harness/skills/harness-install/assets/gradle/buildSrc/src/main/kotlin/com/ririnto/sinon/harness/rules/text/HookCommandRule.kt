package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.core.Severity
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * Rule that requires hooks to declare and run validation commands.
 *
 * Operates on plain text; the check uses literal substring matching for comment lines and requires no AST parser.
 */
object HookCommandRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "hookCommand"

    override fun validate(ctx: RuleContext): Collection<Finding> {
        val parametersObj =
            ctx.manifest
                .categoryObject(category)
                ?.get("parameters")
                ?.jsonObject ?: return emptyList()
        val severity = ctx.manifest.severityOf(category)
        val allowedPreCommitCmds =
            parametersObj["allowedPreCommitCommands"]?.jsonArray
                ?.mapNotNull { elem -> elem.jsonPrimitive.contentOrNull }
                ?: emptyList()
        val prePushHook = ctx.root / JsonAccess.stringFromObject(parametersObj, "prePushHook")
        val preCommitHook = ctx.root / JsonAccess.stringFromObject(parametersObj, "preCommitHook")
        return buildList {
            if (prePushHook.isRegularFile()) {
                addAll(
                    validateHook(
                        ctx,
                        prePushHook.readText(),
                        parametersObj["allowedCommands"]?.jsonArray
                            ?.mapNotNull { elem -> elem.jsonPrimitive.contentOrNull }
                            ?: emptyList(),
                        severity,
                        "pre-push hook",
                    ),
                )
            }
            if (preCommitHook.isRegularFile()) {
                addAll(
                    validateHook(
                        ctx,
                        preCommitHook.readText(),
                        allowedPreCommitCmds,
                        severity,
                        "pre-commit hook",
                        allowedPreCommitCmds.isNotEmpty(),
                    ),
                )
            }
        }
    }

    private fun validateHook(
        ctx: RuleContext,
        text: String,
        allowedCmds: List<String>,
        severity: Severity,
        hookName: String,
        requireDeclaration: Boolean = true,
    ): Collection<Finding> =
        buildList {
            val command = extractCommand(text)
            val missingMsg = { msgKey: String ->
                ctx.manifest.stringValue(category, msgKey).takeIf { msg -> msg.isNotEmpty() }
            }
            if (command.isEmpty() && requireDeclaration) {
                add(
                    Finding(
                        severity,
                        category,
                        missingMsg("missingDeclaration") ?: "$hookName must declare Harness validation command",
                    ),
                )
            }
            if (command.isNotEmpty() && command !in allowedCmds) {
                add(
                    Finding(
                        severity,
                        category,
                        missingMsg("unsupportedCommand")
                            ?: "$hookName declares unsupported validation command: $command",
                    ),
                )
            }
            if (command.isNotEmpty() && !text.contains(command)) {
                add(
                    Finding(
                        severity,
                        category,
                        missingMsg("commandNotRun") ?: "$hookName must run the declared validation command",
                    ),
                )
            }
        }

    private fun extractCommand(text: String): String =
        text
            .lineSequence()
            .firstOrNull { line -> line.startsWith("# Harness validation command: ") }
            ?.removePrefix("# Harness validation command: ")
            ?.trim()
            ?: ""
}
