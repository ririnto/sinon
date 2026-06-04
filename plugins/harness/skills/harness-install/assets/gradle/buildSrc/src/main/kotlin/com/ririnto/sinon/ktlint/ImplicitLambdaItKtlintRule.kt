package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

/**
 * Flags Kotlin lambdas that use implicit `it` parameter; use an explicit parameter name instead.
 */
class ImplicitLambdaItKtlintRule : KtlintRule(
    ruleId = RuleId("code:implicit-lambda-it"),
) {
    override fun visitNode(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile ->
            ktFile.accept(
                object : KtTreeVisitorVoid() {
                    override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
                        super.visitLambdaExpression(lambdaExpression)
                        if (lambdaExpression.valueParameters.isEmpty()) {
                            val lambdaText = lambdaExpression.text
                            val regex = Regex("""\bit\b""")
                            regex.findAll(lambdaText).forEach { match ->
                                val offset = lambdaExpression.textOffset + match.range.first
                                emit(offset, "Kotlin file uses implicit `it` lambda parameter; use an explicit name", false)
                            }
                        }
                    }
                },
            )
        }
    }
}
