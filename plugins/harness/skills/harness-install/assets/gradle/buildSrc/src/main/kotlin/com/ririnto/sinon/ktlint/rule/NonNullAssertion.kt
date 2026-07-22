package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.ElementType.OPERATION_REFERENCE
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.ifAutocorrectAllowed
import com.pinterest.ktlint.rule.engine.core.api.replaceWith
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Flags every Kotlin non-null assertion operator (`!!`) and rewrites it to an explicit `requireNotNull(...)` guard.
 *
 * When the operand is already wrapped in `requireNotNull(...)` or `checkNotNull(...)`, the redundant `!!` is simply stripped.
 *
 * A user-defined callable in the same file with a guard name (function, property, or import) disables autocorrect so the rewritten call never silently resolves to the user declaration instead of the Kotlin standard library guard.
 */
class NonNullAssertion :
    Rule(
        ruleId = RuleId("code:non-null-assertion"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        val GUARD_FUNCTIONS: Set<String> = setOf("requireNotNull", "checkNotNull")
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if (node.elementType == OPERATION_REFERENCE && node.firstChildNode?.elementType == KtTokens.EXCLEXCL) {
            (node.treeParent?.psi as? KtPostfixExpression)?.let { postfixExpression ->
                postfixExpression.baseExpression?.let { baseExpression ->
                    val file = postfixExpression.containingFile as? KtFile
                    val imports = file?.importDirectives.orEmpty()
                    val shadowNames = when (imports.any { directive -> directive.isAllUnder }) {
                        true -> GUARD_FUNCTIONS
                        else ->
                            file?.let { kf ->
                                PsiTreeUtil.findChildrenOfType(kf, KtNamedDeclaration::class.java)
                                    .mapNotNull { declaration -> declaration.name }
                                    .filter { name -> name in GUARD_FUNCTIONS }
                                    .toSet()
                            }.orEmpty() +
                                imports.asSequence()
                                    .filter { directive -> !directive.isAllUnder }
                                    .mapNotNull { directive ->
                                        directive.aliasName?.let { aliasName ->
                                            aliasName.takeIf { name -> name in GUARD_FUNCTIONS }
                                        }
                                    }
                                    .toSet() +
                                imports.asSequence()
                                    .filter { directive -> directive.aliasName === null }
                                    .mapNotNull { directive ->
                                        directive.importedFqName?.takeIf { fqName ->
                                            fqName.parent().asString() != "kotlin"
                                        }?.shortName()?.asString()
                                            ?.takeIf { name -> name in GUARD_FUNCTIONS }
                                    }
                                    .toSet()
                    }
                    val canCorrect = shadowNames.isEmpty()
                    emit(
                        node.startOffset,
                        when (canCorrect) {
                            true -> "avoid non-null assertion `${KtTokens.EXCLEXCL.value}`; use safe call (${KtTokens.SAFE_ACCESS.value}), Elvis (${KtTokens.ELVIS.value}), or an explicit `requireNotNull` guard"
                            else -> "avoid non-null assertion `${KtTokens.EXCLEXCL.value}`; a user-defined `${shadowNames.first()}` shadows the standard library guard, so rewrite it manually with `requireNotNull(...)` or rename the shadow"
                        },
                        canCorrect
                    ).ifAutocorrectAllowed {
                        postfixExpression.node.replaceWith(
                            KtPsiFactory.contextual(postfixExpression, false)
                                .createExpression(
                                    when ((baseExpression as? KtCallExpression)
                                        ?.calleeExpression
                                        ?.let { callee -> callee as? KtNameReferenceExpression }
                                        ?.getReferencedName() in GUARD_FUNCTIONS) {
                                        true -> baseExpression.text
                                        else -> "requireNotNull(${baseExpression.text})"
                                    }
                                )
                                .node
                        )
                    }
                }
            }
        }
    }
}
