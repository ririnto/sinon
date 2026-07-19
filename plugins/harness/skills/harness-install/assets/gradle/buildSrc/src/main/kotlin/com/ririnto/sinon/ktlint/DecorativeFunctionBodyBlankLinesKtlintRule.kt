package com.ririnto.sinon.ktlint

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
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Flags decorative blank lines inside named function block bodies; preserves spacing around local
 * declarations, local functions, comments, KDoc, and raw strings. Autocorrect collapses the blank
 * line by keeping only the trailing indentation of the whitespace node.
 */
class DecorativeFunctionBodyBlankLinesKtlintRule :
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
            !(node.treePrev?.psi is KtDeclaration && node.treeNext?.psi is KtDeclaration) &&
            node.treePrev?.psi !is KtNamedFunction &&
            node.treeNext?.psi !is KtNamedFunction &&
            node.treePrev?.isPartOfComment20 != true &&
            node.treeNext?.isPartOfComment20 != true
        ) {
            PsiTreeUtil.getParentOfType(node.psi.parent as KtBlockExpression, KtNamedFunction::class.java, false)?.let { function ->
                if (function.name !== null && function.bodyBlockExpression !== null) {
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
}
