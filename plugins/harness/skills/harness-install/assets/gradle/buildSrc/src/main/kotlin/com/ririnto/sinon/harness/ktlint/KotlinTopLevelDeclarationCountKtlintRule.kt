package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.ririnto.sinon.harness.core.RuleContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags files with multiple top-level declarations; restrict to a single declaration per file.
 */
class KotlinTopLevelDeclarationCountKtlintRule(
    ctx: RuleContext,
) : HarnessKtlintRule(
        category = CATEGORY,
        messageTemplate = messageTemplate(ctx, CATEGORY),
    ) {
    /**
     * Manifest category backing this rule.
     */
    companion object {
        private const val CATEGORY: String = "kotlinTopLevelDeclarationCount"
    }

    private val allowedDeclarations: Set<String> = ctx.manifest
        .categoryObject(CATEGORY)
        ?.get("parameters")
        ?.jsonObject
        ?.get("allowedDeclarations")
        ?.jsonArray
        ?.mapNotNull { element -> element.jsonPrimitive.contentOrNull }
        ?.toSet()
        ?: setOf("class", "object", "typealias")

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            val declarations = ktFile.declarations
            val firstKind = when (declarations.firstOrNull()) {
                is KtClass -> "class"
                is KtObjectDeclaration -> "object"
                is KtTypeAlias -> "typealias"
                else -> "unknown"
            }
            val violates = declarations.size != 1 ||
                (firstKind != "unknown" && firstKind !in allowedDeclarations)
            if (violates) {
                emit(0, message(), false)
            }
        }
    }
}
