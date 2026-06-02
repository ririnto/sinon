package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.ririnto.sinon.harness.core.RuleContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Requires KDoc comments to use multiline block style `/** */` instead of single-line.
 */
class MultilineDocStyleKtlintRule(
    ctx: RuleContext,
) : HarnessKtlintRule(
        category = CATEGORY,
        messageTemplate = messageTemplate(ctx, CATEGORY),
    ) {
    /**
     * Manifest category backing this rule.
     */
    companion object {
        private const val CATEGORY: String = "multilineDocStyle"
    }

    private val docStyleMode = loadDocStyleMode(ctx)

    private fun loadDocStyleMode(ctx: RuleContext): String =
        ctx.manifest
            .categoryObject(CATEGORY)
            ?.get("parameters")
            ?.jsonObject
            ?.get("docStyleMode")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: "multiline"

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (docStyleMode != "multiline") {
            return
        }
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitDeclaration(declaration: KtDeclaration) {
                        super.visitDeclaration(declaration)
                        val docComment = declaration.docComment ?: return
                        if (!docComment.text.contains('\n')) {
                            emit(docComment.textOffset, message(), false)
                        }
                    }
                },
            )
        }
    }
}
