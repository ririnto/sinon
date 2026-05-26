package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines

/**
 * Rule that requires hooks to have correct shebang.
 *
 * Operates on plain text; the check targets a fixed-position single line and requires no AST parser.
 */
object HookShebangRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "hookShebang"

    override fun validate(ctx: RuleContext): Collection<Finding> =
        buildList {
            val catObj = ctx.manifest.categoryObject(category)
            val parametersObj = catObj?.get("parameters")?.jsonObject
            if (catObj != null && parametersObj != null) {
                addAll(
                    ctx.manifest
                        .stringArray(category, "hooks")
                        .filter { hookPath ->
                            val hook = ctx.root / hookPath
                            when {
                                !hook.isRegularFile() -> {
                                    false
                                }

                                else -> {
                                    (hook.readLines().firstOrNull() ?: "") !=
                                        JsonAccess.stringFromObject(parametersObj, "expectedShebang")
                                }
                            }
                        }.map { hookPath ->
                            Finding(
                                ctx.manifest.severityOf(category),
                                category,
                                ctx.manifest.stringValue(category, "default").takeIf { message ->
                                    message.isNotEmpty()
                                }
                                    ?: "$hookPath must start with ${JsonAccess.stringFromObject(
                                        parametersObj,
                                        "expectedShebang",
                                    )}",
                            )
                        },
                )
            }
        }
}
