package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.isPartOfComment20
import com.pinterest.ktlint.rule.engine.core.api.replaceTextWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtAnonymousInitializer
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtSecondaryConstructor

/**
 * Removes decorative blank lines from the statement blocks of named functions, initializers, accessors, and secondary constructors.
 *
 * The rule covers nested blocks and lambdas inside those bodies.
 * Blank lines around local functions, local classes, annotations, comments, and KDoc remain available for structure.
 * Kotest registration scopes keep their blank lines because the sibling spacing rule owns them.
 */
class FunctionBodyBlankLines :
    Rule(
        ruleId = RuleId("harness:function-body-blank-lines"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if (with(ElementType) { node.elementType == WHITE_SPACE } &&
            1 < node.text.count { character -> character == '\n' } &&
            node.psi.parent is KtBlockExpression &&
            !node.treePrev.keepsSeparation() &&
            !node.treeNext.keepsSeparation() &&
            node.psi.isInsideFunctionBody() &&
            !node.psi.isInsideRegistrationScope()
        ) {
            emit(
                node.psi.textOffset,
                "remove the decorative blank line from the function body",
                true
            ).ifAutocorrectAllowed {
                node.replaceTextWith("\n${node.text.substringAfterLast('\n')}")
            }
        }
    }

    private fun ASTNode?.keepsSeparation(): Boolean =
        this !== null &&
            (psi is KtNamedFunction || psi is KtClassOrObject || psi is KtAnnotationEntry || isPartOfComment20)

    private fun PsiElement.isInsideFunctionBody(): Boolean =
        generateSequence(parent, PsiElement::getParent).any { scope ->
            (scope is KtNamedFunction && scope.name !== null) ||
                scope is KtFunctionLiteral ||
                scope is KtAnonymousInitializer ||
                scope is KtPropertyAccessor ||
                scope is KtSecondaryConstructor
        }

    private fun PsiElement.isInsideRegistrationScope(): Boolean =
        (parent as? KtBlockExpression)?.let(KotestRegistrations::isRegistrationScope) == true
}
