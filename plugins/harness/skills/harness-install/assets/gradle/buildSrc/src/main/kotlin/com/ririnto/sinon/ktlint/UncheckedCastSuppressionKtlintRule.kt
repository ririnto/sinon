package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfig
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfigProperty
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
        override fun visitAnnotationEntry(annotation: KtAnnotationEntry) {
            super.visitAnnotationEntry(annotation)
            if (annotation.calleeExpression?.node?.findChildByType(KtTokens.IDENTIFIER)?.text == Suppress::class.java.simpleName &&
                buildSet {
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
                }.intersect(forbiddenTokens).isNotEmpty()
            ) {
                emit(
                    annotation.textOffset,
                    "avoid suppression of forbidden tokens (`${annotation.text}`); refactor to type-safe cast or explicit handling",
                    false
                )
            }
        }

        private fun extractStringValue(expr: KtStringTemplateExpression): String? =
            expr.entries.joinToString("") { entry -> entry.text }
                .takeIf { expr.entries.all { entry -> entry is KtLiteralStringTemplateEntry } }
    }
}
