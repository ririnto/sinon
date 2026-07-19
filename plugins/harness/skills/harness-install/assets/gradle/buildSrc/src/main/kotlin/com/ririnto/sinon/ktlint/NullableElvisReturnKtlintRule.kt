package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags `val x = nullableLookup ?: return fallback` patterns in functions; prefer returning the
 * nullable lookup as a single expression with `let` and an explicit parameter so the early return
 * stays visible at the call site instead of hiding behind a local binding.
 */
class NullableElvisReturnKtlintRule :
    Rule(
        ruleId = RuleId("code:nullable-elvis-return"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(ElvisReturnVisitor(emit))
    }

    private class ElvisReturnVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitProperty(property: KtProperty) {
            super.visitProperty(property)
            (property.initializer as? KtBinaryExpression)?.let { initializer ->
                initializer.left?.let { left ->
                    if (
                        (
                            left is KtArrayAccessExpression ||
                                left is KtCallExpression ||
                                left is KtNameReferenceExpression ||
                                left is KtSafeQualifiedExpression ||
                                (left is KtQualifiedExpression && left.selectorExpression is KtCallExpression)
                        ) &&
                        initializer.operationReference.text == "?:" &&
                        (initializer.right as? KtReturnExpression)?.returnedExpression != null
                    ) {
                        emit(
                            property.textOffset,
                            "return nullable lookup `${property.name ?: "property"}` as an expression with `let` and an explicit parameter instead of binding then returning",
                            false
                        )
                    }
                }
            }
        }
    }
}
