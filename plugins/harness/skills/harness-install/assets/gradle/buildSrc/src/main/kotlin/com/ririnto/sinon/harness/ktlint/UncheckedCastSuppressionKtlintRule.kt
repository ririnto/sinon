package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.ririnto.sinon.harness.core.RuleContext
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Verifies that @Suppress annotations do not use forbidden suppression tokens.
 */
class UncheckedCastSuppressionKtlintRule(
    ctx: RuleContext,
) : HarnessKtlintRule(
        category = CATEGORY,
        messageTemplate = messageTemplate(ctx, CATEGORY),
    ) {
    /**
     * Manifest category backing this rule.
     */
    companion object {
        private const val CATEGORY: String = "uncheckedCastSuppression"
        private const val DEFAULT_FORBIDDEN = "UNCHECKED_CAST"
    }

    private val forbiddenTokens = ctx.manifest.stringArray(CATEGORY, "forbiddenSuppressions")
        .ifEmpty { listOf(DEFAULT_FORBIDDEN) }
        .toSet()

    private val allowedTokens = ctx.manifest.stringArray(CATEGORY, "allowedSuppressions").toSet()

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitAnnotationEntry(annotation: KtAnnotationEntry) {
                        super.visitAnnotationEntry(annotation)
                        if (annotation.shortName?.asString() == "Suppress") {
                            if (extractSuppressTokens(annotation).intersect(forbiddenTokens - allowedTokens).isNotEmpty()) {
                                emit(annotation.textOffset, message(mapOf("snippet" to annotation.text)), false)
                            }
                        }
                    }

                    private fun extractSuppressTokens(annotation: KtAnnotationEntry): Set<String> =
                        buildSet {
                            for (arg in annotation.valueArguments) {
                                val argExpr = arg.getArgumentExpression()
                                when {
                                    argExpr is KtStringTemplateExpression -> {
                                        val stringValue = extractStringValue(argExpr)
                                        if (stringValue.isNotEmpty()) {
                                            add(stringValue)
                                        }
                                    }
                                    argExpr is KtCollectionLiteralExpression -> {
                                        for (token in arrayLiteralTokens(argExpr)) {
                                            add(token)
                                        }
                                    }
                                }
                            }
                        }

                    private fun arrayLiteralTokens(arrayExpr: PsiElement): List<String> =
                        generateSequence(listOf(arrayExpr)) { layer ->
                            layer.flatMap { element -> element.children.toList() }.takeIf { children -> children.isNotEmpty() }
                        }.flatten()
                            .filterIsInstance<KtStringTemplateExpression>()
                            .map(::extractStringValue)
                            .filter { value -> value.isNotEmpty() }
                            .toList()

                    private fun extractStringValue(expr: KtStringTemplateExpression): String =
                        when {
                            expr.entries.all { entry -> entry is KtLiteralStringTemplateEntry } -> {
                                expr.entries.joinToString("") { entry -> entry.text }
                            }
                            else -> {
                                ""
                            }
                        }
                },
            )
        }
    }
}
