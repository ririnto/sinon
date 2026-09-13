package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.WHITE_SPACE
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.replaceTextWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Removes decorative blank lines from named function block bodies.
 *
 * Blank lines around declarations, annotations, comments, and KDoc remain available for structure.
 */
class FunctionBodyBlankLines :
    Rule(
        ruleId = RuleId("harness:function-body-blank-lines"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if (
            node.elementType == WHITE_SPACE &&
            node.text.count { character -> character == '\n' } > 1 &&
            node.psi.parent is KtBlockExpression &&
            node.treePrev?.psi !is KtNamedFunction &&
            node.treeNext?.psi !is KtNamedFunction &&
            node.treeNext?.psi !is KtDeclaration &&
            node.treeNext?.psi !is KtAnnotationEntry &&
            node.treePrev?.isPartOfComment20 != true &&
            node.treeNext?.isPartOfComment20 != true &&
            node.treePrev
                ?.psi
                ?.text
                ?.contains("ktlint-") != true &&
            node.treeNext
                ?.psi
                ?.text
                ?.contains("ktlint-") != true &&
            node.treePrev
                ?.psi
                ?.text
                ?.contains("ktlint-disable") != true &&
            node.treeNext
                ?.psi
                ?.text
                ?.contains("ktlint-disable") != true &&
            node.treePrev
                ?.psi
                ?.text
                ?.contains("ktlint-enable") != true &&
            node.treeNext
                ?.psi
                ?.text
                ?.contains("ktlint-enable") != true
        ) {
            ((node.psi.parent as? KtBlockExpression)?.parent as? KtNamedFunction)
                ?.takeIf { function -> function.name != null && function.bodyBlockExpression == node.psi.parent }
                ?.let {
                    emit(
                        node.psi.textOffset,
                        "remove the decorative blank line from the function body",
                        true
                    ).takeIf { decision -> decision == AutocorrectDecision.ALLOW_AUTOCORRECT }
                        ?.let { node.replaceTextWith("\n${node.text.substringAfterLast('\n')}") }
                }
        }
    }
}
