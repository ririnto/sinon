package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class RegexConstructor :
    Rule(
        ruleId = RuleId("harness:no-regex-constructor"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        const val KOTLIN_PACKAGE: String = "kotlin"
        const val KOTLIN_REGEX: String = "kotlin.text.Regex"
        const val REGEX_NAME: String = "Regex"
        const val TEXT_SEGMENT: String = "text"
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
            val qualifiedParent = expression.parent as? KtDotQualifiedExpression
            val isKotlinTextRegex = qualifiedParent?.selectorExpression == expression && qualifiedParent.isKotlinTextReceiver()
            val isUnqualifiedRegex = qualifiedParent?.selectorExpression != expression && file?.hasNoConflictingRegexName() == true
            if (callee !== null &&
                file !== null &&
                callee.getReferencedName() == REGEX_NAME &&
                (isKotlinTextRegex || isUnqualifiedRegex)
            ) {
                emit(
                    callee.textOffset,
                    "avoid `Regex(...)` constructor; use `String.toRegex()` instead",
                    false
                )
            }
        }

        private fun KtFile.hasNoConflictingRegexName(): Boolean =
            importDirectives.none { directive ->
                val importedPath = directive.importPath?.pathStr
                (directive.aliasName ?: importedPath?.substringAfterLast('.')) == REGEX_NAME &&
                    importedPath != KOTLIN_REGEX
            } &&
                PsiTreeUtil
                    .findChildrenOfType(this, KtNamedDeclaration::class.java)
                    .none { declaration -> declaration.name == REGEX_NAME }

        private fun KtDotQualifiedExpression.isKotlinTextReceiver(): Boolean =
            when (val receiver = receiverExpression) {
                is KtDotQualifiedExpression -> {
                    val packageRoot = receiver.receiverExpression
                    val packageSegment = receiver.selectorExpression
                    packageRoot is KtNameReferenceExpression &&
                        packageSegment is KtNameReferenceExpression &&
                        packageRoot.getReferencedName() == KOTLIN_PACKAGE &&
                        packageSegment.getReferencedName() == TEXT_SEGMENT
                }

                else -> {
                    false
                }
            }
    }
}
