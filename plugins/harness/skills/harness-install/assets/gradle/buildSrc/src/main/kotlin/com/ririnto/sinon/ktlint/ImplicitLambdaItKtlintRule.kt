package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Flags Kotlin lambdas that use implicit `it` parameter; use an explicit parameter name instead.
 */
class ImplicitLambdaItKtlintRule :
    Rule(
        ruleId = RuleId("code:implicit-lambda-it"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(ImplicitItVisitor(emit))
    }

    private class ImplicitItVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
            super.visitLambdaExpression(lambdaExpression)
            if (lambdaExpression.valueParameters.isEmpty()) {
                val implicitItReferences = lambdaExpression.bodyExpression
                    ?.collectDescendantsOfType<KtNameReferenceExpression>()
                    ?.filter { expression ->
                        expression.getReferencedName() == "it" &&
                            generateSequence(expression.parent) { parent -> parent.parent }
                                .filterIsInstance<KtLambdaExpression>()
                                .firstOrNull() == lambdaExpression
                    }.orEmpty()
                if (implicitItReferences.isNotEmpty()) {
                    emit(
                        implicitItReferences.first().textOffset,
                        "Kotlin file uses implicit `it` lambda parameter; use an explicit name",
                        true
                    ).ifAutocorrectAllowed {
                        val originalText = lambdaExpression.node.text
                        val match = Regex("^\\{(\\s*)").find(originalText)
                        if (match != null) {
                            val whitespace = match.groupValues[1]
                            val insertion = if (whitespace.contains('\n')) {
                                val indent = whitespace.substringAfterLast('\n')
                                whitespace + "it ->\n" + indent
                            } else {
                                whitespace + "it -> "
                            }
                            val rewritten = "{" + insertion + originalText.substring(match.range.last + 1)
                            lambdaExpression.node.replaceWith(
                                KtPsiFactory.contextual(lambdaExpression, false).createExpression(rewritten).node
                            )
                        }
                    }
                }
            }
        }
    }
}
