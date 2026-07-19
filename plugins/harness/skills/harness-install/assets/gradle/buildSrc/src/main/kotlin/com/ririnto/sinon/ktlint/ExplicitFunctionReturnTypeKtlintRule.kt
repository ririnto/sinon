package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags named expression-bodied functions without an explicit return type; declare the return type
 * so the signature stays stable when the body changes.
 */
class ExplicitFunctionReturnTypeKtlintRule :
    Rule(
        ruleId = RuleId("code:explicit-function-return-type"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(FunctionReturnTypeVisitor(emit))
    }

    private class FunctionReturnTypeVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            super.visitNamedFunction(function)
            if (function.name !== null && function.typeReference === null && !function.hasBlockBody()) {
                emit(
                    function.nameIdentifier?.textOffset ?: function.textOffset,
                    "named function `${function.name}` must declare an explicit return type",
                    false
                )
            }
        }
    }
}
