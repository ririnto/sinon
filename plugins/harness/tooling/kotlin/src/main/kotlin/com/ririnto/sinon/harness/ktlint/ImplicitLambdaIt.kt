package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Requires an explicit name for every lambda parameter that would otherwise use implicit `it`.
 *
 * Autocorrection is limited to lambdas whose first body token has a stable brace layout.
 */
class ImplicitLambdaIt :
    Rule(
        ruleId = RuleId("harness:implicit-lambda-it"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtFile)?.accept(ImplicitItVisitor(emit))
    }

    private class ImplicitItVisitor(
        private val emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) : KtTreeVisitorVoid() {
        override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
            super.visitLambdaExpression(lambdaExpression)
            if (!lambdaExpression.functionLiteral.hasParameterSpecification()) {
                val implicitItReferences =
                    lambdaExpression.bodyExpression
                        ?.collectDescendantsOfType<KtNameReferenceExpression>()
                        ?.filter { reference -> reference.getReferencedName() == "it" }
                        ?.filter { reference -> reference.resolvesTo(lambdaExpression) }
                        .orEmpty()
                implicitItReferences.firstOrNull()?.let { reference ->
                    val parameterName = findParameterName(lambdaExpression)
                    emit(
                        reference.textOffset,
                        "use an explicit name for the implicit `it` lambda parameter",
                        parameterName !== null
                    ).ifAutocorrectAllowed {
                        parameterName?.let { name ->
                            replaceImplicitParameter(lambdaExpression, implicitItReferences, name)
                        }
                    }
                }
            }
        }

        private fun findParameterName(lambdaExpression: KtLambdaExpression): String? {
            val declarations =
                lambdaExpression.bodyExpression
                    ?.collectDescendantsOfType<KtNamedDeclaration>()
                    .orEmpty()
            val references =
                lambdaExpression.bodyExpression
                    ?.collectDescendantsOfType<KtNameReferenceExpression>()
                    .orEmpty()
            val enclosingDeclarations =
                generateSequence(lambdaExpression.parent, PsiElement::getParent)
                    .filterIsInstance<KtNamedDeclaration>()
                    .toList()
            val enclosingParameters =
                generateSequence(lambdaExpression.parent, PsiElement::getParent)
                    .filterIsInstance<KtLambdaExpression>()
                    .flatMap { element -> element.valueParameters.asSequence() }
            return generateSequence("value") { name -> "${name}Value" }
                .firstOrNull { name ->
                    declarations.none { declaration -> declaration.name == name } &&
                        references.none { reference -> reference.getReferencedName() == name } &&
                        enclosingDeclarations.none { declaration -> declaration.name == name } &&
                        enclosingParameters.none { parameter -> parameter.name == name }
                }
        }

        private fun KtNameReferenceExpression.resolvesTo(lambdaExpression: KtLambdaExpression): Boolean {
            val ancestors = generateSequence(parent, PsiElement::getParent).toList()
            return isUnqualifiedValueReference() &&
                lambdaExpression in ancestors &&
                ancestors
                    .takeWhile { ancestor -> ancestor !== lambdaExpression }
                    .none { ancestor -> ancestor.shadowsImplicitIt(this) }
        }

        private fun KtNameReferenceExpression.isUnqualifiedValueReference(): Boolean {
            val selector = (parent as? KtCallExpression)?.takeIf { call -> call.calleeExpression === this } ?: this
            return (selector.parent as? KtQualifiedExpression)?.selectorExpression !== selector &&
                (parent as? KtCallableReferenceExpression)?.callableReference !== this &&
                parent !is KtUserType
        }

        private fun PsiElement.shadowsImplicitIt(reference: KtNameReferenceExpression): Boolean =
            when (this) {
                is KtLambdaExpression -> {
                    !functionLiteral.hasParameterSpecification() || valueParameters.any { parameter -> parameter.name == "it" }
                }

                is KtNamedFunction -> {
                    valueParameters.any { parameter -> parameter.name == "it" }
                }

                is KtCatchClause -> {
                    catchParameter?.name == "it"
                }

                is KtWhenExpression -> {
                    subjectVariable?.let { variable ->
                        variable.name == "it" && variable.textRange.endOffset <= reference.textOffset
                    } == true
                }

                is KtForExpression -> {
                    PsiTreeUtil.isAncestor(body, reference, false) &&
                        (loopParameter?.name == "it" || destructuringDeclaration?.entries?.any { entry -> entry.name == "it" } == true)
                }

                is KtBlockExpression -> {
                    statements.any { statement ->
                        when (statement) {
                            is KtProperty -> {
                                statement.name == "it" && statement.textRange.endOffset <= reference.textOffset
                            }

                            is KtDestructuringDeclaration -> {
                                statement.textRange.endOffset <= reference.textOffset &&
                                    statement.entries.any { entry -> entry.name == "it" }
                            }

                            is KtNamedFunction, is KtClassOrObject -> {
                                (statement as KtNamedDeclaration).shadowsItReference(reference)
                            }

                            else -> {
                                false
                            }
                        }
                    }
                }

                is KtClassBody -> {
                    declarations.filterIsInstance<KtNamedDeclaration>().any { declaration -> declaration.shadowsItReference(reference) }
                }

                is KtClassOrObject -> {
                    primaryConstructorParameters.any { parameter -> parameter.name == "it" }
                }

                else -> {
                    false
                }
            }

        private fun KtNamedDeclaration.shadowsItReference(reference: KtNameReferenceExpression): Boolean =
            name == "it" &&
                when (this) {
                    is KtNamedFunction -> {
                        (reference.parent as? KtCallExpression)?.calleeExpression === reference
                    }

                    is KtClassOrObject -> {
                        this is KtObjectDeclaration ||
                            this is KtEnumEntry ||
                            (reference.parent as? KtCallExpression)?.calleeExpression === reference
                    }

                    else -> {
                        true
                    }
                }

        private fun replaceImplicitParameter(
            lambdaExpression: KtLambdaExpression,
            references: List<KtNameReferenceExpression>,
            parameterName: String
        ) {
            lambdaExpression.text.run {
                val leftBraceOffset = indexOf('{')
                if (0 <= leftBraceOffset) {
                    val rewrittenText =
                        references
                            .map { reference -> reference.textOffset - lambdaExpression.textOffset to reference.textLength }
                            .sortedByDescending { (offset, _) -> offset }
                            .fold(this) { currentText, (offset, length) ->
                                currentText.replaceRange(offset, offset + length, parameterName)
                            }
                    val body = rewrittenText.substring(leftBraceOffset + 1)
                    if (body.isNotEmpty()) {
                        val whitespace = body.takeWhile(Char::isWhitespace)
                        val parameter =
                            when ('\n' in whitespace) {
                                true -> """$whitespace$parameterName ->
${whitespace.substringAfterLast('\n')}"""

                                false -> "$whitespace$parameterName -> "
                            }
                        lambdaExpression.node.replaceWith(
                            KtPsiFactory
                                .contextual(lambdaExpression, false)
                                .createExpression("{$parameter${body.substring(whitespace.length)}")
                                .node
                        )
                    }
                }
            }
        }
    }
}
