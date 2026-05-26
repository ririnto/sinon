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
import kotlin.io.path.name
import kotlin.io.path.readText

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
        val catObj = ctx.manifest.categoryObject(category) ?: return emptyList()
        val parametersObj = catObj.get("parameters")?.jsonObject ?: return emptyList()
        val markerTemplate = JsonAccess.stringFromObject(parametersObj, "markerTemplate")
        val placeholderForbidden = JsonAccess.stringFromObject(parametersObj, "placeholderForbidden")
        return buildList {
            addAll(
                ctx.manifest.stringArray(category, "hooks")
                    .filter { hookPath -> (ctx.root / hookPath).isRegularFile() }
                    .flatMap { hookPath ->
                        val hook = ctx.root / hookPath
                        val text = hook.readText()
                        val marker = markerTemplate.replace("{name}", hook.name)
                        buildList {
                            if (!text.contains(marker)) {
                                add(
                                    Finding(
                                        ctx.manifest.severityOf(category),
                                        category,
                                        ctx.manifest.stringValue(category, "missingMarker").takeIf { message -> message.isNotEmpty() }
                                            ?: "$hookPath must contain generated marker '$marker'",
                                    ),
                                )
                            }
                            if (text.contains(placeholderForbidden)) {
                                add(
                                    Finding(
                                        ctx.manifest.severityOf(category),
                                        category,
                                        ctx.manifest.stringValue(category, "placeholderPresent").takeIf { message -> message.isNotEmpty() }
                                            ?: "$hookPath still contains packaging placeholder text",
                                    ),
                                )
                            }
                        }
                    },
            )
        }
    }
}
