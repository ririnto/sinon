package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.json.jsonObject

/**
 * Rule that requires hooks to have generated marker and no packaging placeholders.
 *
 * Operates on plain text; the check uses literal substring matching and requires no AST parser.
 */
object HookGeneratedMarkerRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "hookGeneratedMarker"

    override fun validate(ctx: RuleContext): Collection<Finding> {
        val parametersObj = (ctx.manifest.categoryObject(category) ?: return emptyList()).get("parameters")?.jsonObject ?: return emptyList()
        val markerTemplate = JsonAccess.stringFromObject(parametersObj, "markerTemplate")
        val placeholderForbidden = JsonAccess.stringFromObject(parametersObj, "placeholderForbidden")
        val hooks = JsonAccess.stringArrayFromObject(parametersObj, "hooks")
        return buildList {
            addAll(
                hooks
                    .mapNotNull { hookPath -> HookPathSupport.safeHookPath(ctx.root, hookPath)?.let { hook -> hookPath to hook } }
                    .flatMap { (hookPath, hook) ->
                        val text = hook.readText()
                        buildList {
                            if (!text.contains(markerTemplate.replace("{name}", hook.name))) {
                                add(
                                    Finding(
                                        ctx.manifest.severityOf(category),
                                        category,
                                        ctx.manifest.stringValue(category, "missingMarker").takeIf { message ->
                                            message.isNotEmpty()
                                        }
                                            ?: "$hookPath must contain generated marker '${markerTemplate.replace("{name}", hook.name)}'",
                                    ),
                                )
                            }
                            if (text.contains(placeholderForbidden)) {
                                add(
                                    Finding(
                                        ctx.manifest.severityOf(category),
                                        category,
                                        ctx.manifest.stringValue(category, "placeholderPresent").takeIf { message ->
                                            message.isNotEmpty()
                                        }
                                            ?: "$hookPath still contains packaging placeholder text",
                                    ),
                                )
                            }
                        }
                    },
            )
        }
    }

    /**
     * Adds missing generated markers to hook files.
     *
     * Fixes only missingMarker findings (SAFE); placeholderForbidden findings are MANUAL and skipped.
     */
    override fun format(ctx: RuleContext): Collection<Path> {
        val parametersObj = (ctx.manifest.categoryObject(category) ?: return emptyList()).get("parameters")?.jsonObject ?: return emptyList()
        val markerTemplate = JsonAccess.stringFromObject(parametersObj, "markerTemplate")
        return buildList {
            JsonAccess.stringArrayFromObject(parametersObj, "hooks")
                .forEach { hookPath ->
                    val hook = HookPathSupport.safeHookPath(ctx.root, hookPath) ?: return@forEach
                    val text = hook.readText()
                    val expectedMarker = markerTemplate.replace("{name}", hook.name)
                    if (!text.contains(expectedMarker)) {
                        hook.writeText("$expectedMarker\n$text")
                        add(hook)
                    }
                }
        }
    }
}
