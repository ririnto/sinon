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
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * Rule that requires executable scripts under certain directories to use /usr/bin/env shebang.
 *
 * Operates on plain text; the check targets a fixed-position first line and requires no AST parser.
 */
object EnvShebangUsageRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "envShebangUsage"
    override fun validate(ctx: RuleContext): Collection<Finding> = buildList {
        val catObj = ctx.manifest.categoryObject(category)
        val parametersObj = catObj?.get("parameters")?.jsonObject
        if (catObj != null && parametersObj != null) {
            ctx.manifest.stringArray(category, "directories")
                .filter { dirPath ->
                    (ctx.root / dirPath).isDirectory()
                }.flatMap { dirPath ->
                    ctx.walkSafe(ctx.root / dirPath).paths
                        .filter { file -> file.isExecutable() }
                        .filter { file ->
                            (file.readText().lineSequence().firstOrNull() ?: "").startsWith("#!")
                        }
                        .filter { file ->
                            !(file.readText().lineSequence().firstOrNull() ?: "").startsWith(JsonAccess.stringFromObject(parametersObj, "expectedPrefix"))
                        }
                        .map { file ->
                            Finding(
                                ctx.manifest.severityOf(category),
                                category,
                                ctx.manifest.stringValue(category, "default").takeIf { message ->
                                    message.isNotEmpty()
                                }
                                    ?: "executable script should use /usr/bin/env shebang: ${file.relativeTo(
                                        ctx.root,
                                    )}",
                            )
                        }
                }.forEach { finding -> add(finding) }
        }
    }

}
