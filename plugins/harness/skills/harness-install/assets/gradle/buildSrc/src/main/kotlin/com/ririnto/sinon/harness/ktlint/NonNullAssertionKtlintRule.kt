package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.OPERATION_REFERENCE
import com.ririnto.sinon.harness.core.RuleContext
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

/**
 * Flags Kotlin non-null assertion operators (`!!`); use safe call, Elvis, or an explicit guard instead.
 */
class NonNullAssertionKtlintRule(
    ctx: RuleContext,
) : HarnessKtlintRule(
        category = CATEGORY,
        messageTemplate = messageTemplate(ctx, CATEGORY),
    ) {
    /**
     * Manifest category backing this rule.
     */
    companion object {
        private const val CATEGORY: String = "nonNullAssertion"
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        if (node.elementType == OPERATION_REFERENCE && node.text == "!!") {
            val expression = node.treeParent?.firstChildNode?.text ?: node.text
            emit(node.startOffset, message(mapOf("expression" to expression)), false)
        }
    }
}
