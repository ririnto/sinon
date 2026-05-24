package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
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
    override fun validate(ctx: RuleContext): Collection<Finding> = buildList {
        val catObj = ctx.manifest.categoryObject(category)
        if (catObj != null) {
            addAll(
                ctx.manifest.stringArray(category, "hooks")
                    .filter { hookPath ->
                        val hook = ctx.root / hookPath
                        hook.isRegularFile()
                    }
                    .filter { hookPath ->
                        val hook = ctx.root / hookPath
                        !hook.isExecutable()
                    }
                    .map { hookPath ->
                        Finding(
                            ctx.manifest.severityOf(category),
                            category,
                            ctx.manifest.stringValue(category, "default").takeIf { message ->
                                message.isNotEmpty()
                            }
                                ?: "$hookPath must be executable",
                        )
                    }
            )
        }
    }

}
