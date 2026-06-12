package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.OPERATION_REFERENCE
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

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
        if (node.elementType == OPERATION_REFERENCE && node.text == "!!") {
            emit(
                node.startOffset,
                "avoid non-null assertion `!!` on `${node.treeParent?.firstChildNode?.text ?: node.text}`; use safe call (?.), Elvis (?:), or explicit guard",
                false
            )
        }
    }
}
