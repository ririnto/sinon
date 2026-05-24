package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import kotlinx.serialization.json.contentOrNull
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
    override fun validate(ctx: RuleContext): Collection<Finding> = buildList {
        val severity = ctx.manifest.severityOf(category)
        val catObj = ctx.manifest.categoryObject(category)
        val parametersObj = catObj?.get("parameters")?.jsonObject
        if (catObj != null && parametersObj != null) {
            val allowedPreCommitCmds = JsonAccess.stringArrayFromObject(parametersObj["allowedPreCommitCommands"]?.jsonObject, "gradle")
            val prePushHook = ctx.root / JsonAccess.stringFromObject(parametersObj, "prePushHook")
            val preCommitHook = ctx.root / JsonAccess.stringFromObject(parametersObj, "preCommitHook")
            if (prePushHook.isRegularFile()) {
                val text = prePushHook.readText()
                val command =
                    text
                        .lineSequence()
                        .firstOrNull { line ->
                            line.startsWith("# Harness validation command: ")
                        }?.removePrefix("# Harness validation command: ")
                        ?.trim()
                        ?: ""
                if (command.isEmpty()) {
                    add(
                        Finding(
                            severity,
                            category,
                            ctx.manifest.stringValue(category, "missingDeclaration").takeIf { message ->
                                message.isNotEmpty()
                            } ?: "pre-push hook must declare Harness validation command",
                        ),
                    )
                }
                if (command.isNotEmpty() && command !in JsonAccess.stringArrayFromObject(parametersObj["allowedCommands"]?.jsonObject, "gradle")) {
                    add(
                        Finding(
                            severity,
                            category,
                            ctx.manifest.stringValue(category, "unsupportedCommand").takeIf { message ->
                                message.isNotEmpty()
                            } ?: "pre-push hook declares unsupported validation command: $command",
                        ),
                    )
                }
                if (command.isNotEmpty() && !text.contains(command)) {
                    add(
                        Finding(
                            severity,
                            category,
                            ctx.manifest.stringValue(category, "commandNotRun").takeIf { message ->
                                message.isNotEmpty()
                            } ?: "pre-push hook must run the declared validation command",
                        ),
                    )
                }
            }
            if (preCommitHook.isRegularFile()) {
                val text = preCommitHook.readText()
                val command =
                    text
                        .lineSequence()
                        .firstOrNull { line ->
                            line.startsWith("# Harness validation command: ")
                        }?.removePrefix("# Harness validation command: ")
                        ?.trim()
                        ?: ""
                if (command.isEmpty() && allowedPreCommitCmds.isNotEmpty()) {
                    add(
                        Finding(
                            severity,
                            category,
                            ctx.manifest.stringValue(category, "missingDeclaration").takeIf { message ->
                                message.isNotEmpty()
                            } ?: "pre-commit hook must declare validation command",
                        ),
                    )
                }
                if (command.isNotEmpty() && command !in allowedPreCommitCmds) {
                    add(
                        Finding(
                            severity,
                            category,
                            ctx.manifest.stringValue(category, "unsupportedCommand").takeIf { message ->
                                message.isNotEmpty()
                            } ?: "pre-commit hook declares unsupported validation command: $command",
                        ),
                    )
                }
                if (command.isNotEmpty() && !text.contains(command)) {
                    add(
                        Finding(
                            severity,
                            category,
                            ctx.manifest.stringValue(category, "commandNotRun").takeIf { message ->
                                message.isNotEmpty()
                            } ?: "pre-commit hook must run the declared validation command",
                        ),
                    )
                }
            }
        }
    }

}
