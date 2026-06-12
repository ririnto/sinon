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

    private var forbiddenTokens: Set<String> = emptySet()
    private var allowedTokens: Set<String> = emptySet()

    override fun beforeFirstNode(editorConfig: EditorConfig) {
        forbiddenTokens =
            editorConfig[FORBIDDEN_SUPPRESSIONS]
                .split(",")
                .map { token -> token.trim() }
                .filter { token -> token.isNotEmpty() }
                .toSet()
        allowedTokens =
            editorConfig[ALLOWED_SUPPRESSIONS]
                .split(",")
                .map { token -> token.trim() }
                .filter { token -> token.isNotEmpty() }
                .toSet()
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)
            ?.takeUnless { ktFile -> ktFile.isScript() }
            ?.let { ktFile ->
                ktFile.accept(
                    object : KtTreeVisitorVoid() {
                        override fun visitAnnotationEntry(annotation: KtAnnotationEntry) {
                            super.visitAnnotationEntry(annotation)
                            if (annotation.shortName?.asString() == "Suppress") {
                                if (extractSuppressTokens(annotation)
                                        .intersect(forbiddenTokens - allowedTokens)
                                        .isNotEmpty()
                                ) {
                                    emit(
                                        annotation.textOffset,
                                        "avoid suppression of forbidden tokens (`${annotation.text}`); refactor to type-safe cast or explicit handling",
                                        false
                                    )
                                }
                            }
                        }

                        private fun extractSuppressTokens(annotation: KtAnnotationEntry): Set<String> =
                            buildSet {
                                for (arg in annotation.valueArguments) {
                                    val argExpr = arg.getArgumentExpression()
                                    when {
                                        argExpr is KtStringTemplateExpression -> {
                                            val stringValue = extractStringValue(argExpr)
                                            if (stringValue.isNotEmpty()) {
                                                add(stringValue)
                                            }
                                        }

                                        argExpr is KtCollectionLiteralExpression -> {
                                            for (token in arrayLiteralTokens(argExpr)) {
                                                add(token)
                                            }
                                        }
                                    }
                                }
                            }

                        private fun arrayLiteralTokens(arrayExpr: PsiElement): List<String> =
                            generateSequence(listOf(arrayExpr)) { layer ->
                                layer
                                    .flatMap { element -> element.children.toList() }
                                    .takeIf { children -> children.isNotEmpty() }
                            }.flatten()
                                .filterIsInstance<KtStringTemplateExpression>()
                                .map(::extractStringValue)
                                .filter { value -> value.isNotEmpty() }
                                .toList()

                        private fun extractStringValue(expr: KtStringTemplateExpression): String =
                            when {
                                expr.entries.all { entry -> entry is KtLiteralStringTemplateEntry } -> {
                                    expr.entries.joinToString("") { entry -> entry.text }
                                }

                                else -> {
                                    ""
                                }
                            }
                    }
                )
            }
    }
}
