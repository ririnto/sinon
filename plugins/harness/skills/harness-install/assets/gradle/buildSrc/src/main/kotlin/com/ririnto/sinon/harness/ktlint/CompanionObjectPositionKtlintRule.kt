package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.ririnto.sinon.harness.core.RuleContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags companion objects positioned outside the configured location; reposition to top or bottom of class body.
 */
class CompanionObjectPositionKtlintRule(
    ctx: RuleContext,
) : HarnessKtlintRule(
        category = CATEGORY,
        messageTemplate = messageTemplate(ctx, CATEGORY),
    ) {
    /**
     * Manifest category backing this rule.
     */
    companion object {
        private const val CATEGORY: String = "companionObjectPosition"
    }

    private val position: String = ctx.manifest
        .categoryObject(CATEGORY)
        ?.get("parameters")
        ?.jsonObject
        ?.get("position")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf { value -> value == "bottom" }
        ?: "top"

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitClass(klass: KtClass) {
                        super.visitClass(klass)
                        klass.getBody()?.let { body ->
                            val declarations = body.declarations.filter { declaration -> declaration !is KtEnumEntry }
                            declarations.forEachIndexed { index, declaration ->
                                if (declaration is KtObjectDeclaration && declaration.isCompanion()) {
                                    val lastIndex = declarations.lastIndex
                                    val isWrongPosition = when (position) {
                                        "bottom" -> index != lastIndex
                                        else -> index != 0
                                    }
                                    if (isWrongPosition) {
                                        emit(
                                            declaration.textOffset,
                                            message(mapOf("position" to position)),
                                            false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}
