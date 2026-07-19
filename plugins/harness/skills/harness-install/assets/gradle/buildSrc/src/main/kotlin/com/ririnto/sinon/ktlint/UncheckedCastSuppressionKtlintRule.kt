package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfigProperty
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import org.ec4j.core.model.PropertyType
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Verifies that @Suppress annotations do not use forbidden suppression tokens.
 */
class UncheckedCastSuppressionKtlintRule :
    Rule(
        ruleId = RuleId("code:unchecked-cast-suppression"),
        about = About(),
        usesEditorConfigProperties = setOf(FORBIDDEN_SUPPRESSIONS, ALLOWED_SUPPRESSIONS)
    ),
    RuleAutocorrectApproveHandler {
    companion object {
        private val FORBIDDEN_SUPPRESSIONS: EditorConfigProperty<String> =
            EditorConfigProperty(
                type =
                    PropertyType(
                        "ktlint_unchecked_cast_suppression_forbidden",
                        "Comma separated forbidden suppression tokens",
                        PropertyType.PropertyValueParser.IDENTITY_VALUE_PARSER
                    ),
                defaultValue = "UNCHECKED_CAST"
            )

        private val ALLOWED_SUPPRESSIONS: EditorConfigProperty<String> =
            EditorConfigProperty(
                type =
                    PropertyType(
                        "ktlint_unchecked_cast_suppression_allowed",
                        "Comma separated allowed suppression tokens",
                        PropertyType.PropertyValueParser.IDENTITY_VALUE_PARSER
                    ),
                defaultValue = ""
            )
    }

    private lateinit var forbiddenTokens: Set<String>
    private lateinit var allowedTokens: Set<String>

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        forbiddenTokens = parseTokens(editorConfig[FORBIDDEN_SUPPRESSIONS])
        allowedTokens = parseTokens(editorConfig[ALLOWED_SUPPRESSIONS])
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(SuppressAnnotationVisitor(forbiddenTokens - allowedTokens, emit))
    }

    private fun parseTokens(value: String): Set<String> =
        value.split(",").map { token -> token.trim() }.filter { token -> token.isNotEmpty() }.toSet()

    private class SuppressAnnotationVisitor(
        private val forbiddenTokens: Set<String>,
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        private val castRelatedTokens = setOf(
            "UNCHECKED_CAST",
            "USELESS_CAST",
            "CAST_NEVER_SUCCEEDS",
            "UNCHECKED_CAST_IN_SUSPEND"
        )

        override fun visitAnnotationEntry(annotation: KtAnnotationEntry) {
            super.visitAnnotationEntry(annotation)
            if (annotation.calleeExpression?.text != Suppress::class.java.simpleName) {
                return
            }

            val forbiddenToken = annotation.valueArguments
                .singleOrNull()
                ?.getArgumentExpression()
                ?.let { argument ->
                    (argument as? KtStringTemplateExpression)?.let(::extractStringValue)
                }
            val detectedTokens = buildSet {
                for (arg in annotation.valueArguments) {
                    val argExpr = arg.getArgumentExpression()
                    when (argExpr) {
                        is KtStringTemplateExpression -> {
                            extractStringValue(argExpr)?.let { stringValue -> add(stringValue) }
                        }

                        is KtCollectionLiteralExpression -> {
                            addAll(
                                generateSequence(listOf<PsiElement>(argExpr)) { layer ->
                                    layer
                                        .flatMap { element -> element.children.toList() }
                                        .takeIf { children -> children.isNotEmpty() }
                                }.flatten()
                                    .filterIsInstance<KtStringTemplateExpression>()
                                    .mapNotNull(::extractStringValue)
                            )
                        }
                    }
                }
            }
            val detectedToken = detectedTokens.firstOrNull { token -> token in forbiddenTokens }

            if (detectedToken != null) {
                val canBeAutoCorrected = forbiddenToken == detectedToken &&
                    detectedToken in castRelatedTokens &&
                    annotation.parent?.parent?.let { scopeOwner ->
                        containsCastToken(scopeOwner.node).not()
                    } == true
                emit(
                    annotation.textOffset,
                    "avoid suppression of forbidden tokens (`${annotation.text}`); refactor to type-safe cast or explicit handling",
                    canBeAutoCorrected
                ).ifAutocorrectAllowed {
                    if (canBeAutoCorrected) {
                        val annotationNode = annotation.node
                        val parent = annotationNode.treeParent
                        val nextSibling = annotationNode.treeNext
                        val previousSibling = parent.treePrev
                        val isOnlyAnnotation = parent.getChildren(null).count { child ->
                            child.elementType != ElementType.WHITE_SPACE
                        } == 1
                        if (nextSibling != null &&
                            nextSibling.elementType == ElementType.WHITE_SPACE &&
                            nextSibling.text.contains("\n")
                        ) {
                            parent.removeChild(nextSibling)
                        }
                        parent.removeChild(annotationNode)
                        if (isOnlyAnnotation &&
                            previousSibling != null &&
                            previousSibling.elementType == ElementType.WHITE_SPACE &&
                            previousSibling.text.contains("\n")
                        ) {
                            parent.treeParent.removeChild(previousSibling)
                        }
                        if (parent.getChildren(null).all { child -> child.elementType == ElementType.WHITE_SPACE }) {
                            val parentParent = parent.treeParent
                            val parentNextSibling = parent.treeNext
                            if (parentNextSibling != null &&
                                parentNextSibling.elementType == ElementType.WHITE_SPACE &&
                                parentNextSibling.text.contains("\n")
                            ) {
                                parentParent.removeChild(parentNextSibling)
                            }
                            parentParent.removeChild(parent)
                        }
                    }
                }
            }
        }

        private fun containsCastToken(node: ASTNode): Boolean =
            node.elementType == KtTokens.AS_KEYWORD ||
                node.elementType == KtTokens.AS_SAFE ||
                node.getChildren(null).any(::containsCastToken)

        private fun extractStringValue(expr: KtStringTemplateExpression): String? =
            expr.entries.joinToString("") { entry -> entry.text }
                .takeIf { expr.entries.all { entry -> entry is KtLiteralStringTemplateEntry } }
    }
}
