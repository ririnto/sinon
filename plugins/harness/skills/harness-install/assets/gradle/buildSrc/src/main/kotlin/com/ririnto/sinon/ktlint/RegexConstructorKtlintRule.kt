package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags `Regex(...)` constructor calls; prefer `String.toRegex()` for single-argument literal patterns.
 * Autocorrects only when the call has one positional argument; otherwise the finding stays manual.
 * Skips files that import or declare a conflicting `Regex` name that is not `kotlin.text.Regex`.
 */
class RegexConstructorKtlintRule :
    Rule(
        ruleId = RuleId("code:no-regex-constructor"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        const val KOTLIN_REGEX: String = "kotlin.text.Regex"
        const val REGEX_NAME: String = "Regex"
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(RegexConstructorVisitor(emit))
    }

    private class RegexConstructorVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            super.visitCallExpression(expression)
            val callee = expression.calleeExpression as? KtNameReferenceExpression
            val file = expression.containingFile as? KtFile
            if (
                callee !== null &&
                file !== null &&
                callee.getReferencedName() == REGEX_NAME &&
                !(
                    file.importDirectives.any { directive ->
                        val importedPath = directive.importPath?.pathStr
                        (directive.aliasName ?: importedPath?.substringAfterLast('.')) == REGEX_NAME &&
                            importedPath != KOTLIN_REGEX
                    } ||
                        PsiTreeUtil
                            .findChildrenOfType(file, KtNamedDeclaration::class.java)
                            .any { declaration -> declaration.name == REGEX_NAME }
                )
            ) {
                val pattern =
                    expression.valueArguments
                        .singleOrNull()
                        ?.takeIf { argument -> argument.getArgumentName() === null }
                        ?.getArgumentExpression()
                val canAutocorrect = pattern is KtStringTemplateExpression
                emit(
                    callee.textOffset,
                    "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                    canAutocorrect
                ).ifAutocorrectAllowed {
                    if (pattern is KtStringTemplateExpression) {
                        expression.node.replaceWith(
                            KtPsiFactory.contextual(expression, false)
                                .createExpression("${pattern.text}.toRegex()")
                                .node
                        )
                    }
                }
            }
        }
    }
}
