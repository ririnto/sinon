package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
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
        (node.psi as? KtFile)
            ?.takeUnless { ktFile -> ktFile.isScript() }
            ?.let { ktFile ->
                ktFile.accept(
                    object : KtTreeVisitorVoid() {
                        override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                            super.visitLambdaExpression(lambdaExpression)
                            if (lambdaExpression.valueParameters.isEmpty()) {
                                lambdaExpression.bodyExpression
                                    ?.collectDescendantsOfType<KtNameReferenceExpression>()
                                    ?.filter { expression ->
                                        expression.getReferencedName() == "it" &&
                                            expression.nearestParentLambda() == lambdaExpression
                                    }?.forEach { expression ->
                                        emit(
                                            expression.textOffset,
                                            "Kotlin file uses implicit `it` lambda parameter; use an explicit name",
                                            false
                                        )
                                    }
                            }
                        }
                    }
                )
            }
    }

    private tailrec fun KtNameReferenceExpression.nearestParentLambda(
        current: PsiElement? = parent
    ): KtLambdaExpression? =
        when (current) {
            null -> null
            is KtLambdaExpression -> current
            else -> nearestParentLambda(current.parent)
        }
}
