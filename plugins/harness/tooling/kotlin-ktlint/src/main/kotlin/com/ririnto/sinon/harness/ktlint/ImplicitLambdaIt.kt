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
            if (lambdaExpression.valueParameters.isNotEmpty()) {
                return
            }
            val implicitItReferences =
                lambdaExpression.bodyExpression
                    ?.collectDescendantsOfType<KtNameReferenceExpression>()
                    ?.filter { reference ->
                        reference.getReferencedName() == "it" &&
                            generateSequence(reference.parent) { parent -> parent.parent }
                                .filterIsInstance<KtLambdaExpression>()
                                .firstOrNull() == lambdaExpression
                    }.orEmpty()
            implicitItReferences.firstOrNull()?.let { reference ->
                emit(
                    reference.textOffset,
                    "use an explicit name for the implicit `it` lambda parameter",
                    true
                ).ifAutocorrectAllowed {
                    lambdaExpression.node.text
                        .substringAfter('{', missingDelimiterValue = "")
                        .takeIf { body -> body.isNotEmpty() }
                        ?.let { body ->
                            val whitespace = body.takeWhile { character -> character.isWhitespace() }
                            val parameter =
                                if ('\n' in whitespace) {
                                    "${whitespace}value ->\n${whitespace.substringAfterLast('\n')}"
                                } else {
                                    "${whitespace}value -> "
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
