package com.ririnto.sinon.ktlint.rule

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
 * Flags Kotlin lambdas that use implicit `it` parameter.
 *
 * Use an explicit parameter name instead.
 */
class ImplicitLambdaIt :
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
                        "use an explicit name for the implicit `it` lambda parameter",
                        true
                    ).ifAutocorrectAllowed {
                        val originalText = lambdaExpression.node.text
                        "^\\{(?<whitespace>\\s*)".toRegex().find(originalText)?.let { match ->
                            val whitespace = match.groups["whitespace"]?.value.orEmpty()
                            lambdaExpression.node.replaceWith(
                                KtPsiFactory.contextual(lambdaExpression, false).createExpression(
                                    "{${when (whitespace.contains('\n')) {
                                        true -> "${whitespace}it ->\n${whitespace.substringAfterLast('\n')}"
                                        else -> "${whitespace}it -> "
                                    }}${originalText.substring(match.range.last + 1)}"
                                ).node
                            )
                        }
                    }
                }
            }
        }
    }
}
