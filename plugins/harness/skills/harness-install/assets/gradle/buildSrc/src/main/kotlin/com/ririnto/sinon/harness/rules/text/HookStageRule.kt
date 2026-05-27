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
import kotlin.io.path.readText

/**
 * Rule that requires hooks to contain stage markers.
 */
object HookStageRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "hookStage"

    override fun validate(ctx: RuleContext): Collection<Finding> =
        buildList {
            val catObj = ctx.manifest.categoryObject(category)
            val parametersObj = catObj?.get("parameters")?.jsonObject
            val stagesObj = parametersObj?.get("stages")?.jsonObject
            if (catObj != null && parametersObj != null && stagesObj != null) {
                val markerTemplate = JsonAccess.stringFromObject(parametersObj, "markerTemplate")
                val preCommitStage = JsonAccess.stringFromObject(stagesObj, "pre-commit")
                val prePushStage = JsonAccess.stringFromObject(stagesObj, "pre-push")
                val preCommitHook = ctx.root / "docs/harness/git-hooks/pre-commit"
                val prePushHook = ctx.root / "docs/harness/git-hooks/pre-push"
                if (preCommitHook.isRegularFile()) {
                    if (!preCommitHook.readText().contains(markerTemplate.replace("{stage}", preCommitStage))) {
                        add(
                            Finding(
                                ctx.manifest.severityOf(category),
                                category,
                                ctx.manifest.stringValue(category, "default").takeIf { message ->
                                    message.isNotEmpty()
                                }
                                    ?: "pre-commit must contain stage marker '${markerTemplate.replace(
                                        "{stage}",
                                        preCommitStage,
                                    )}'",
                            ),
                        )
                    }
                }
                if (prePushHook.isRegularFile()) {
                    if (!prePushHook.readText().contains(markerTemplate.replace("{stage}", prePushStage))) {
                        add(
                            Finding(
                                ctx.manifest.severityOf(category),
                                category,
                                ctx.manifest.stringValue(category, "default").takeIf { message ->
                                    message.isNotEmpty()
                                }
                                    ?: "pre-push must contain stage marker '${markerTemplate.replace(
                                        "{stage}",
                                        prePushStage,
                                    )}'",
                            ),
                        )
                    }
                }
            }
        }
}
