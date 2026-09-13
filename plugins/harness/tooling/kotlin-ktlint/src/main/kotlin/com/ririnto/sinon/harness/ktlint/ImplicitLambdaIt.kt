package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
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
            if (lambdaExpression.functionLiteral.hasParameterSpecification()) {
                return
            }
            val implicitItReferences =
                lambdaExpression.bodyExpression
                    ?.collectDescendantsOfType<KtNameReferenceExpression>()
                    ?.filter { reference ->
                        reference.getReferencedName() == "it" && reference.resolvesTo(lambdaExpression)
                    }.orEmpty()
            implicitItReferences.firstOrNull()?.let { reference ->
                val parameterName = findParameterName(lambdaExpression)
                emit(
                    reference.textOffset,
                    "use an explicit name for the implicit `it` lambda parameter",
                    parameterName != null
                ).ifAutocorrectAllowed {
                    parameterName?.let { name ->
                        replaceImplicitParameter(lambdaExpression, implicitItReferences, name)
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
                generateSequence(lambdaExpression.parent) { element -> element.parent }
                    .filterIsInstance<KtNamedDeclaration>()
                    .toList()
            val enclosingParameters =
                generateSequence(lambdaExpression.parent) { element -> element.parent }
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
            val ancestors = generateSequence(parent) { element -> element.parent }.toList()
            if (lambdaExpression !in ancestors) {
                return false
            }
            return ancestors
                .takeWhile { ancestor -> ancestor != lambdaExpression }
                .filterIsInstance<KtLambdaExpression>()
                .none { nestedLambda -> nestedLambda.shadowsImplicitIt() }
        }

        private fun KtLambdaExpression.shadowsImplicitIt(): Boolean =
            !functionLiteral.hasParameterSpecification() ||
                valueParameters.any { parameter -> parameter.name == "it" }

        private fun replaceImplicitParameter(
            lambdaExpression: KtLambdaExpression,
            references: List<KtNameReferenceExpression>,
            parameterName: String
        ) {
            val lambdaText = lambdaExpression.text
            val leftBraceOffset = lambdaText.indexOf('{')
            if (leftBraceOffset < 0) {
                return
            }
            val rewrittenText =
                references
                    .map { reference -> reference.textOffset - lambdaExpression.textOffset to reference.textLength }
                    .sortedByDescending { (offset, _) -> offset }
                    .fold(lambdaText) { currentText, (offset, length) ->
                        currentText.replaceRange(offset, offset + length, parameterName)
                    }
            val body = rewrittenText.substring(leftBraceOffset + 1)
            if (body.isEmpty()) {
                return
            }
            val whitespace = body.takeWhile { character -> character.isWhitespace() }
            val parameter =
                if ('\n' in whitespace) {
                    "$whitespace$parameterName ->\n${whitespace.substringAfterLast('\n')}"
                } else {
                    "$whitespace$parameterName -> "
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
