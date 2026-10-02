package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReturnExpression

/**
 * Flags mid-block `if` branches that exit a function or loop before later statements.
 *
 * The rule avoids labeled exits and lambda bodies because their targets need semantic resolution.
 */
class MidFunctionExit :
    Rule(
        ruleId = RuleId("harness:mid-function-exit"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtIfExpression)?.let { expression -> expression.reportIfNecessary(emit) }
    }

    private fun KtIfExpression.reportIfNecessary(
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        simpleExitKind()?.let { exitKind ->
            when {
                hasLaterStatementInBlock() &&
                    executionBoundary() is KtNamedFunction &&
                    (exitKind == ExitKind.RETURN || hasLoopInExecutionScope()) -> {
                    emit(textOffset, exitKind.message, false)
                }
            }
        }
    }

    private fun KtIfExpression.simpleExitKind(): ExitKind? =
        when (val thenExit = then.simpleExitKind()) {
            null -> `else`.simpleExitKind()
            else -> thenExit
        }

    private fun KtIfExpression.hasLaterStatementInBlock(): Boolean =
        (parent as? KtBlockExpression)?.statements?.lastOrNull()?.let { lastStatement -> lastStatement !== this } == true

    private fun KtIfExpression.executionBoundary(): PsiElement? =
        generateSequence(parent, PsiElement::getParent)
            .firstOrNull { element -> element is KtLambdaExpression || element is KtNamedFunction }

    private fun KtIfExpression.hasLoopInExecutionScope(): Boolean =
        generateSequence(parent, PsiElement::getParent)
            .takeWhile { element -> element !is KtLambdaExpression && element !is KtNamedFunction }
            .any { element -> element is KtLoopExpression }

    private fun KtExpression?.simpleExitKind(): ExitKind? =
        when (this) {
            is KtBlockExpression -> statements.singleOrNull()?.simpleExitKind()
            is KtReturnExpression -> ExitKind.RETURN.takeIf { getTargetLabel() === null }
            is KtBreakExpression -> ExitKind.BREAK.takeIf { getTargetLabel() === null }
            is KtContinueExpression -> ExitKind.CONTINUE.takeIf { getTargetLabel() === null }
            else -> null
        }

    private enum class ExitKind(
        private val keyword: String,
        private val action: String
    ) {
        RETURN("return", "Invert the condition or use `?.let` only for optional work when return semantics stay unchanged"),
        BREAK("break", "Move the condition into the loop or invert it when behavior stays unchanged"),
        CONTINUE("continue", "Invert the condition and keep the main path clear when behavior stays unchanged");

        val message: String
            get() = "Avoid a mid-block `if` with `$keyword`. $action"
    }
}
