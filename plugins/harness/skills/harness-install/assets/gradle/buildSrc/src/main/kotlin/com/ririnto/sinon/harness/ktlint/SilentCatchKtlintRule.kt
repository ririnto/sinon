package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.ktlint.PsiTraversal.descendantsOfType
import com.ririnto.sinon.harness.ktlint.PsiTraversal.hasDescendantOfType
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags catch blocks that silently ignore the caught exception without logging or handling it.
 */
class SilentCatchKtlintRule(
    ctx: RuleContext,
) : HarnessKtlintRule(
        category = CATEGORY,
        messageTemplate = messageTemplate(ctx, CATEGORY),
    ) {
    /**
     * Manifest category backing this rule.
     */
    companion object {
        private const val CATEGORY: String = "silentCatch"
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitCatchSection(catchSection: KtCatchClause) {
                        super.visitCatchSection(catchSection)
                        val paramName = catchSection.catchParameter?.name
                        val body = catchSection.catchBody
                        if (
                            paramName != null &&
                            body != null &&
                            !body.hasDescendantOfType<KtNameReferenceExpression> { expression -> expression.getReferencedName() == paramName } &&
                            !body.hasDescendantOfType<KtThrowExpression>() &&
                            !body.hasDescendantOfType<KtCallExpression> { expression -> isLoggingCall(expression) }
                        ) {
                            emit(catchSection.textOffset, message(), false)
                        }
                    }

                    private fun isLoggingCall(expression: KtCallExpression): Boolean {
                        val callName = expression.calleeExpression?.text ?: return false
                        val receiver = (expression.parent as? KtDotQualifiedExpression)
                            ?.takeIf { qualified -> qualified.selectorExpression == expression }
                            ?.receiverExpression
                            ?.text
                        return when {
                            callName == "println" -> true
                            callName == "log" -> true
                            receiver == "logger" -> true
                            receiver == "log" -> true
                            receiver?.endsWith(".logger") == true -> true
                            receiver?.endsWith(".log") == true -> true
                            else -> false
                        }
                    }
                },
            )
        }
    }
}
