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
import org.jetbrains.kotlin.lexer.KtTokens
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
                                if (body !== null &&
                                    (body is KtStringTemplateExpression || body is KtConstantExpression || !body.isUnitExpression())
                                ) {
                                    val modifierList = function.modifierList
                                    val typeName =
                                        if (modifierList?.hasModifier(KtTokens.OVERRIDE_KEYWORD) == true ||
                                            modifierList?.hasModifier(KtTokens.EXTERNAL_KEYWORD) == true ||
                                            modifierList?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true ||
                                            generateSequence(function.parent) { element -> element.parent }
                                                .filterIsInstance<KtDeclaration>()
                                                .any { declaration ->
                                                    declaration.modifierList?.hasModifier(KtTokens.EXPECT_KEYWORD) == true
                                                }
                                        ) {
                                            null
                                        } else {
                                            with(LiteralTypeInference) { body.inferLiteralType() }
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
                                                    KtPsiFactory
                                                        .contextual(function, false)
                                                        .createFunction(
                                                            "${currentText.substring(
                                                                0,
                                                                currentText.findInsertionPosition(eqIndex)
                                                            )}: $type ${currentText.substring(eqIndex)}"
                                                        ).node
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            if (typeReference.isUnitTypeReference()) {
                                emit(
                                    typeReference.textOffset,
                                    "omit the redundant `Unit` return type on named function `$functionName`",
                                    true
                                ).ifAutocorrectAllowed {
                                    val functionNode = function.node
                                    val nodesToRemove = typeReference.node.collectNodesThroughColon()
                                    val whitespaceNodes = nodesToRemove.lastOrNull()?.treePrev.collectWhitespaceNodes()
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

    private tailrec fun ASTNode?.collectNodesThroughColon(collected: List<ASTNode> = emptyList()): List<ASTNode> =
        when {
            this === null -> collected
            elementType == KtTokens.COLON -> collected + this
            else -> treePrev.collectNodesThroughColon(collected + this)
        }

    private tailrec fun ASTNode?.collectWhitespaceNodes(collected: List<ASTNode> = emptyList()): List<ASTNode> =
        when {
            this === null -> {
                collected
            }

            text.all { character -> character.isWhitespace() } -> {
                treePrev.collectWhitespaceNodes(collected + this)
            }

            else -> {
                collected
            }
        }

    private tailrec fun String.findInsertionPosition(position: Int): Int =
        when (0 < position && this[position - 1].isWhitespace()) {
            true -> findInsertionPosition(position - 1)
            else -> position
        }

    private fun KtExpression.isUnitExpression(): Boolean =
        when (this) {
            is KtNameReferenceExpression -> {
                getReferencedName() == "Unit"
            }

            is KtDotQualifiedExpression -> {
                val receiver = receiverExpression
                val selector = selectorExpression
                receiver is KtNameReferenceExpression &&
                    receiver.getReferencedName() == "kotlin" &&
                    selector is KtNameReferenceExpression &&
                    selector.getReferencedName() == "Unit"
            }

            else -> {
                false
            }
        }

    private fun KtTypeReference.isUnitTypeReference(): Boolean {
        val userType = typeElement as? KtUserType
        val referenceExpression = userType?.referenceExpression
        val qualifier = userType?.qualifier
        return when {
            referenceExpression !is KtNameReferenceExpression -> {
                false
            }

            referenceExpression.getReferencedName() != "Unit" -> {
                false
            }

            qualifier === null -> {
                true
            }

            else -> {
                val qualifierReferenceExpression = qualifier.referenceExpression
                qualifier.qualifier === null &&
                    qualifierReferenceExpression is KtNameReferenceExpression &&
                    qualifierReferenceExpression.getReferencedName() == "kotlin"
            }
        }
    }
}
