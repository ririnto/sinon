package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.OPERATION_REFERENCE
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Flags Kotlin non-null assertion operators (`!!`); use safe call, Elvis, or an explicit guard instead.
 */
class NonNullAssertionKtlintRule :
    Rule(
        ruleId = RuleId("code:non-null-assertion"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if (node.elementType == OPERATION_REFERENCE && node.firstChildNode?.elementType == KtTokens.EXCLEXCL) {
            val postfixExpression = node.treeParent?.psi as? KtPostfixExpression
            val baseExpression = postfixExpression?.baseExpression
            val calleeName = (baseExpression as? KtCallExpression)
                ?.calleeExpression as? KtNameReferenceExpression
            val canAutocorrect = calleeName?.getReferencedName() in setOf("requireNotNull", "checkNotNull")
            emit(
                node.startOffset,
                "avoid non-null assertion `${KtTokens.EXCLEXCL.value}` on `${node.treeParent?.firstChildNode?.text ?: node.text}`; " +
                    "use safe call (${KtTokens.SAFE_ACCESS.value}), Elvis (${KtTokens.ELVIS.value}), or explicit guard",
                canAutocorrect
            ).ifAutocorrectAllowed {
                if (canAutocorrect && postfixExpression !== null && baseExpression !== null) {
                    postfixExpression.node.replaceWith(
                        KtPsiFactory.contextual(postfixExpression, false)
                            .createExpression(baseExpression.text)
                            .node
                    )
                }
            }
        }
    }
}
