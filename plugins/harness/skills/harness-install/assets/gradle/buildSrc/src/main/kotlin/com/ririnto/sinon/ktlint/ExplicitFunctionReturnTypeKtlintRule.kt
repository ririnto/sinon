package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.lexer.KtTokens

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
                val typeName = autocorrectType(function)
                emit(
                    function.nameIdentifier?.textOffset ?: function.textOffset,
                    "named function `${function.name}` must declare an explicit return type",
                    typeName !== null
                ).ifAutocorrectAllowed {
                    if (typeName != null) {
                        val currentText = function.text
                        val eqIndex = currentText.indexOf('=')
                        if (eqIndex > 0) {
                            var insertPos = eqIndex
                            while (insertPos > 0 && currentText[insertPos - 1].isWhitespace()) insertPos--
                            val newText = currentText.substring(0, insertPos) + ": $typeName " + currentText.substring(eqIndex)
                            val newFunction = KtPsiFactory.contextual(function, false).createFunction(newText)
                            function.node.replaceWith(newFunction.node)
                        }
                    }
                }
            }
        }

        private fun autocorrectType(function: KtNamedFunction): String? {
            val modifierList = function.modifierList
            if (modifierList?.hasModifier(KtTokens.OVERRIDE_KEYWORD) == true ||
                modifierList?.hasModifier(KtTokens.EXTERNAL_KEYWORD) == true ||
                modifierList?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true ||
                function.hasExpectAncestor()
            ) {
                return null
            }
            return when (val body = function.bodyExpression) {
                is KtStringTemplateExpression -> "String"
                is KtConstantExpression -> when (body.node.elementType) {
                    ElementType.BOOLEAN_CONSTANT -> "Boolean"
                    ElementType.CHARACTER_CONSTANT -> "Char"
                    ElementType.INTEGER_CONSTANT -> integerType(body.text)
                    ElementType.FLOAT_CONSTANT -> floatType(body.text)
                    else -> null
                }
                else -> null
            }
        }

        private fun integerType(text: String): String? = when {
            text.endsWith("L", ignoreCase = true) && !text.endsWith("U", ignoreCase = true) -> "Long"
            !text.endsWith("L", ignoreCase = true) && !text.endsWith("U", ignoreCase = true) -> "Int"
            else -> null
        }

        private fun floatType(text: String): String? = when {
            text.endsWith("f", ignoreCase = true) -> "Float"
            else -> "Double"
        }

        private fun KtNamedFunction.hasExpectAncestor(): Boolean =
            generateSequence(parent) { it.parent }
                .filterIsInstance<KtDeclaration>()
                .any { it.modifierList?.hasModifier(KtTokens.EXPECT_KEYWORD) == true }
    }
}
