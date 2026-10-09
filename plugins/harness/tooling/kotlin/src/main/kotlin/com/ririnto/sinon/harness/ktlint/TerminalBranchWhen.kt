package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Flags complete if-else chains that compare one stable function parameter.
 */
class TerminalBranchWhen :
    Rule(
        ruleId = RuleId("harness:terminal-branch-when"),
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
        takeUnless { branch -> branch.isElseIfBranch(parent) }
            ?.completeIfElseChain()
            ?.subjectName()
            ?.takeIf { subjectName -> hasStableFunctionParameter(subjectName) }
            ?.let { _ -> emit(textOffset, "if/else chain compares one subject; use `when (subject)`", false) }
    }

    private fun KtIfExpression.completeIfElseChain(): List<KtIfExpression>? =
        generateSequence(this) { branch -> branch.`else` as? KtIfExpression }
            .toList()
            .takeIf { branches -> branches.lastOrNull()?.`else` !== null }

    private fun List<KtIfExpression>.subjectName(): String? {
        val subjectNames = map { branch -> branch.condition.subjectName() }
        return subjectNames.firstOrNull()?.takeIf { name -> subjectNames.all { candidate -> candidate == name } }
    }

    private fun KtExpression?.subjectName(): String? =
        when (this) {
            is KtBinaryExpression -> {
                when (operationReference.text) {
                    "==" -> (left as? KtNameReferenceExpression)?.getReferencedName()
                    else -> null
                }
            }

            is KtIsExpression -> {
                when (operationReference.text) {
                    "is" -> (leftHandSide as? KtNameReferenceExpression)?.getReferencedName()
                    else -> null
                }
            }

            else -> {
                null
            }
        }

    private fun KtIfExpression.hasStableFunctionParameter(subjectName: String): Boolean {
        val executionBoundary =
            generateSequence(parent, PsiElement::getParent)
                .firstOrNull { element -> element is KtLambdaExpression || element is KtNamedFunction }
        return when (executionBoundary) {
            is KtNamedFunction -> {
                generateSequence(parent, PsiElement::getParent)
                    .takeWhile { element -> element !== executionBoundary }
                    .none { element -> element is KtLambdaExpression } &&
                    executionBoundary.valueParameters.any { parameter -> parameter.name == subjectName } &&
                    executionBoundary.bodyExpression
                        ?.let { body ->
                            body.collectDescendantsOfType<KtProperty>().none { property -> property.name == subjectName } &&
                                body
                                    .collectDescendantsOfType<KtDestructuringDeclarationEntry>()
                                    .none { entry -> entry.name == subjectName }
                        } == true
            }

            else -> {
                false
            }
        }
    }

    private fun KtIfExpression.isElseIfBranch(ancestor: PsiElement?): Boolean =
        generateSequence(ancestor, PsiElement::getParent)
            .any { element -> element is KtIfExpression && element.`else` === this }
}
