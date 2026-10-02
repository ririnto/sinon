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
 * Use `?.let` for optional work, and keep required failures explicit.
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
                    when (left) {
                        is KtArrayAccessExpression,
                        is KtCallExpression,
                        is KtNameReferenceExpression,
                        is KtSafeQualifiedExpression -> {
                            true
                        }

                        is KtQualifiedExpression -> {
                            when (left.selectorExpression) {
                                is KtCallExpression, is KtNameReferenceExpression -> {
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        }

                        else -> {
                            false
                        }
                    }
                if (
                    isNullableLookup &&
                    initializer.operationReference.text == "?:" &&
                    initializer.right is KtReturnExpression
                ) {
                    emit(
                        property.textOffset,
                        "avoid an Elvis-return property guard; use ?.let for optional work and keep required failure explicit",
                        false
                    )
                }
            }
        }
    }
}
