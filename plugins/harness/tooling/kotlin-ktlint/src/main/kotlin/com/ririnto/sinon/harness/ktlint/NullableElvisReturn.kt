package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression

/**
 * Flags properties initialized from a nullable lookup that falls back to an early `return`.
 *
 * Prefer returning the lookup as an expression with `let` and an explicit parameter.
 */
class NullableElvisReturn :
    Rule(
        ruleId = RuleId("harness:nullable-elvis-return"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtProperty)?.let { property ->
            (property.initializer as? KtBinaryExpression)?.let { initializer ->
                val left = initializer.left
                val isNullableLookup =
                    left is KtArrayAccessExpression ||
                        left is KtCallExpression ||
                        left is KtNameReferenceExpression ||
                        left is KtSafeQualifiedExpression ||
                        (
                            left is KtQualifiedExpression &&
                                left.selectorExpression.let { selector ->
                                    selector is KtCallExpression || selector is KtNameReferenceExpression
                                }
                        )
                if (
                    isNullableLookup &&
                    initializer.operationReference.text == "?:" &&
                    (initializer.right as? KtReturnExpression)?.returnedExpression !== null
                ) {
                    emit(
                        property.textOffset,
                        "Return nullable lookups as an expression with let and an explicit parameter",
                        false
                    )
                }
            }
        }
    }
}
