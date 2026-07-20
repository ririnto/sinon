package com.ririnto.sinon.ktlint.rule

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
 * Flags every Kotlin non-null assertion operator (`!!`) and rewrites it to an explicit
 * `requireNotNull(...)` guard. When the operand is already wrapped in `requireNotNull(...)`
 * or `checkNotNull(...)`, the redundant `!!` is simply stripped.
 */
class NonNullAssertion :
    Rule(
        ruleId = RuleId("code:non-null-assertion"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        val GUARD_FUNCTIONS: Set<String> = setOf("requireNotNull", "checkNotNull")
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if (node.elementType === OPERATION_REFERENCE && node.firstChildNode?.elementType === KtTokens.EXCLEXCL) {
            (node.treeParent?.psi as? KtPostfixExpression)?.let { postfixExpression ->
                postfixExpression.baseExpression?.let { baseExpression ->
                    emit(
                        node.startOffset,
                        "avoid non-null assertion `${KtTokens.EXCLEXCL.value}`; use safe call (${KtTokens.SAFE_ACCESS.value}), Elvis (${KtTokens.ELVIS.value}), or an explicit `requireNotNull` guard",
                        true
                    ).ifAutocorrectAllowed {
                        postfixExpression.node.replaceWith(
                            KtPsiFactory.contextual(postfixExpression, false)
                                .createExpression(
                                    when {
                                        ((baseExpression as? KtCallExpression)
                                            ?.calleeExpression as? KtNameReferenceExpression)
                                            ?.getReferencedName() in GUARD_FUNCTIONS -> baseExpression.text
                                        else -> "requireNotNull(${baseExpression.text})"
                                    }
                                )
                                .node
                        )
                    }
                }
            }
        }
    }
}
