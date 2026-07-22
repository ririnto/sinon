package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.WHITE_SPACE
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.replaceTextWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Flags decorative blank lines inside named function block bodies.
 *
 * Preserves spacing before local declarations, annotated declarations, local functions, comments, and KDoc.
 *
 * Autocorrect collapses the blank line by keeping only the trailing indentation of the whitespace node.
 */
class DecorativeFunctionBodyBlankLines :
    Rule(
        ruleId = RuleId("code:no-decorative-function-body-blank-lines"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if (
            node.elementType == WHITE_SPACE &&
            1 < node.text.count { character -> character == '\n' } &&
            node.psi.parent is KtBlockExpression &&
            node.treePrev?.psi !is KtNamedFunction &&
            node.treeNext?.psi !is KtNamedFunction &&
            node.treeNext?.psi !is KtDeclaration &&
            node.treeNext?.psi !is KtAnnotationEntry &&
            node.treePrev?.isPartOfComment20 != true &&
            node.treeNext?.isPartOfComment20 != true
        ) {
            ((node.psi.parent as? KtBlockExpression)?.parent as? KtNamedFunction)
                ?.takeIf { function -> function.name !== null && function.bodyBlockExpression == node.psi.parent }
                ?.let {
                    emit(
                        node.psi.textOffset,
                        "remove decorative blank line from named function body",
                        true
                    ).ifAutocorrectAllowed {
                        node.replaceTextWith("\n${node.text.substringAfterLast('\n')}")
                    }
                }
        }
    }
}
