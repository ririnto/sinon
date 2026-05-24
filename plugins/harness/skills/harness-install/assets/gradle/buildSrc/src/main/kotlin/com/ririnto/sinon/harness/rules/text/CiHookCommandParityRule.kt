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
 * Rule that requires CI files to match hook validation commands.
 *
 * Operates on plain text; the check uses literal substring matching for comment lines across files and requires no AST parser.
 */
object CiHookCommandParityRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "ciHookCommandParity"
    override fun validate(ctx: RuleContext): Collection<Finding> = buildList {
        val catObj = ctx.manifest.categoryObject(category)
        val parametersObj = catObj?.get("parameters")?.jsonObject
        if (catObj != null && parametersObj != null) {
            val command =
                if ((ctx.root / JsonAccess.stringFromObject(parametersObj, "referenceHook")).isRegularFile()) {
                    (ctx.root / JsonAccess.stringFromObject(parametersObj, "referenceHook"))
                        .readText()
                        .lineSequence()
                        .firstOrNull { hookLine ->
                            hookLine.startsWith("# Harness validation command: ")
                        }?.removePrefix("# Harness validation command: ")
                        ?.trim()
                        ?: ""
                } else {
                    ""
                }
            if (command.isNotEmpty()) {
                addAll(
                    ctx.manifest.stringArray(category, "ciFiles")
                        .filter { ciFile -> (ctx.root / ciFile).isRegularFile() }
                        .filter { ciFile -> !(ctx.root / ciFile).readText().contains(command) }
                        .map { ciFile ->
                            Finding(
                                ctx.manifest.severityOf(category),
                                category,
                                ctx.manifest.stringValue(category, "default").takeIf { message ->
                                    message.isNotEmpty()
                                }
                                    ?: "$ciFile: CI command mismatch — expected $command",
                            )
                        }
                )
            }
        }
    }

}
