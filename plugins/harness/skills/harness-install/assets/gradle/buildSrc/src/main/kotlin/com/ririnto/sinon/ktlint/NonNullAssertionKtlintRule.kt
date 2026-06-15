package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.OPERATION_REFERENCE
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens

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
            emit(
                node.startOffset,
                "avoid non-null assertion `${KtTokens.EXCLEXCL.value}` on `${node.treeParent?.firstChildNode?.text ?: node.text}`; " +
                    "use safe call (${KtTokens.SAFE_ACCESS.value}), Elvis (${KtTokens.ELVIS.value}), or explicit guard",
                false
            )
        }
    }
}
