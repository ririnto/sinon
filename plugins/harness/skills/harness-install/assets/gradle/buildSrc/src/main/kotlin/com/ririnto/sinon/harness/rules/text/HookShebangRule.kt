package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

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

    override fun validate(ctx: RuleContext): Collection<Finding> {
        val parametersObj = (ctx.manifest.categoryObject(category) ?: return emptyList())["parameters"]?.jsonObject
            ?: return emptyList()
        val hooks = JsonAccess.stringArrayFromObject(parametersObj, "hooks")
        val expectedShebang = JsonAccess.stringFromObject(parametersObj, "expectedShebang")
        return buildList {
            addAll(
                hooks
                    .filter { hookPath ->
                        val hook = HookPathSupport.safeHookPath(ctx.root, hookPath) ?: return@filter false
                        (hook.readLines().firstOrNull() ?: "") != expectedShebang
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

    override fun format(ctx: RuleContext): Collection<Path> {
        val parametersObj = (ctx.manifest.categoryObject(category) ?: return emptyList())
            .get("parameters")?.jsonObject
            ?: return emptyList()
        val expectedShebang = JsonAccess.stringFromObject(parametersObj, "expectedShebang")
        val hooks = JsonAccess.stringArrayFromObject(parametersObj, "hooks")
        return buildList {
            hooks
                .forEach { hookPath ->
                    val hook = HookPathSupport.safeHookPath(ctx.root, hookPath) ?: return@forEach
                    val currentText = hook.readText()
                    val currentFirstLine = currentText.lines().firstOrNull() ?: ""
                    if (currentFirstLine != expectedShebang) {
                        hook.writeText("$expectedShebang\n$currentText")
                        add(hook)
                    }
                }
        }
    }
}
