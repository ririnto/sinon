package com.ririnto.sinon.ktlint.rule

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
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * Flags named functions with missing or redundant return type declarations.
 * Expression-bodied functions must declare their return type so the signature stays stable when the body changes, unless the body evaluates to `Unit`.
 * Explicit `: Unit` annotations are always redundant and are flagged for removal.
 *
 * `Unit` return types are intentionally omitted.
 */
class ExplicitFunctionReturnType :
    Rule(
        ruleId = RuleId("code:explicit-function-return-type"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if (node.elementType == ElementType.FUN) {
            (node.psi as? KtNamedFunction)?.let { function ->
                function.name?.let { functionName ->
                    when (val typeReference = function.typeReference) {
                        null -> {
                            if (!function.hasBlockBody()) {
                                val body = function.bodyExpression
                                if (body !== null && (body is KtStringTemplateExpression || body is KtConstantExpression || !isUnitExpression(body))) {
                                    val modifierList = function.modifierList
                                    val typeName = if (modifierList?.hasModifier(KtTokens.OVERRIDE_KEYWORD) == true ||
                                        modifierList?.hasModifier(KtTokens.EXTERNAL_KEYWORD) == true ||
                                        modifierList?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true ||
                                        generateSequence(function.parent) { element -> element.parent }
                                            .filterIsInstance<KtDeclaration>()
                                            .any { declaration -> declaration.modifierList?.hasModifier(KtTokens.EXPECT_KEYWORD) == true }
                                    ) {
                                        null
                                    } else {
                                        when (body) {
                                            is KtStringTemplateExpression -> String::class.simpleName
                                            is KtConstantExpression -> when (body.node.elementType) {
                                                ElementType.BOOLEAN_CONSTANT -> Boolean::class.simpleName
                                                ElementType.CHARACTER_CONSTANT -> Char::class.simpleName
                                                ElementType.INTEGER_CONSTANT -> when {
                                                    body.text.contains("U", ignoreCase = true) -> null
                                                    body.text.endsWith("L", ignoreCase = true) -> Long::class.simpleName
                                                    else -> {
                                                        val cleaned = body.text.replace("_", "")
                                                        val (digits, radix) = when {
                                                            cleaned.startsWith("0x", ignoreCase = true) -> cleaned.substring(2) to 16
                                                            cleaned.startsWith("0b", ignoreCase = true) -> cleaned.substring(2) to 2
                                                            else -> cleaned to 10
                                                        }
                                                        when {
                                                            digits.toIntOrNull(radix) != null -> Int::class.simpleName
                                                            digits.toLongOrNull(radix) != null -> Long::class.simpleName
                                                            else -> null
                                                        }
                                                    }
                                                }
                                                ElementType.FLOAT_CONSTANT -> when {
                                                    body.text.endsWith("f", ignoreCase = true) -> Float::class.simpleName
                                                    else -> Double::class.simpleName
                                                }
                                                else -> null
                                            }
                                            else -> null
                                        }
                                    }
                                    emit(
                                        function.nameIdentifier?.textOffset ?: function.textOffset,
                                        "declare an explicit return type on named function `$functionName`",
                                        typeName !== null
                                    ).ifAutocorrectAllowed {
                                        typeName?.let { type ->
                                            val eqNode = function.node.findChildByType(KtTokens.EQ)
                                            if (eqNode !== null) {
                                                val currentText = function.text
                                                val eqIndex = eqNode.startOffset - function.node.startOffset
                                                function.node.replaceWith(
                                                    KtPsiFactory.contextual(function, false)
                                                        .createFunction(
                                                            "${currentText.substring(0, findInsertionPosition(currentText, eqIndex))}: $type ${currentText.substring(eqIndex)}"
                                                        )
                                                        .node
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            if (isUnitTypeReference(typeReference)) {
                                emit(
                                    typeReference.textOffset,
                                    "omit the redundant `Unit` return type on named function `$functionName`",
                                    true
                                ).ifAutocorrectAllowed {
                                    val functionNode = function.node
                                    val nodesToRemove = collectNodesThroughColon(typeReference.node)
                                    val whitespaceNodes = collectWhitespaceNodes(nodesToRemove.lastOrNull()?.treePrev)
                                    nodesToRemove.asReversed().forEach { node -> functionNode.removeChild(node) }
                                    whitespaceNodes
                                        .asReversed()
                                        .forEach { node -> functionNode.removeChild(node) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private tailrec fun collectNodesThroughColon(
        current: ASTNode?,
        collected: List<ASTNode> = emptyList()
    ): List<ASTNode> =
        when {
            current === null -> collected
            current.elementType == KtTokens.COLON -> collected + current
            else -> collectNodesThroughColon(current.treePrev, collected + current)
        }

    private tailrec fun collectWhitespaceNodes(
        current: ASTNode?,
        collected: List<ASTNode> = emptyList()
    ): List<ASTNode> =
        when {
            current === null -> collected
            current.text.all { character -> character.isWhitespace() } ->
                collectWhitespaceNodes(current.treePrev, collected + current)
            else -> collected
        }

    private tailrec fun findInsertionPosition(text: String, position: Int): Int =
        when (0 < position && text[position - 1].isWhitespace()) {
            true -> findInsertionPosition(text, position - 1)
            else -> position
        }

    private fun isUnitExpression(expression: KtExpression): Boolean = when (expression) {
        is KtNameReferenceExpression -> {
            expression.getReferencedName() == "Unit"
        }

        is KtDotQualifiedExpression -> {
            (expression.receiverExpression as? KtNameReferenceExpression)?.getReferencedName() == "kotlin" &&
                (expression.selectorExpression as? KtNameReferenceExpression).getReferencedName() == "Unit"
        }

        else -> false
    }

    private fun isUnitTypeReference(typeReference: KtTypeReference): Boolean {
        val userType = typeReference.typeElement as? KtUserType
        val referenceExpression = userType?.referenceExpression
        val qualifier = userType?.qualifier
        return when {
            referenceExpression !is KtNameReferenceExpression -> false
            referenceExpression.getReferencedName() != "Unit" -> false
            qualifier === null -> true
            qualifier is KtUserType -> {
                val qualifierReferenceExpression = qualifier.referenceExpression
                qualifier.qualifier === null &&
                    qualifierReferenceExpression is KtNameReferenceExpression &&
                    qualifierReferenceExpression.getReferencedName() == "kotlin"
            }
            else -> false
        }
    }
}
