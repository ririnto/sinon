package com.ririnto.sinon.harness.ktlint

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
 * Flags every Kotlin non-null assertion operator (`!!`).
 *
 * The rule never rewrites an assertion into a guard because `requireNotNull` throws `IllegalArgumentException` where `!!` throws `NullPointerException`.
 * The only correction strips a redundant `!!` from a standard library `requireNotNull(...)` or `checkNotNull(...)` call, whose result is already non-null.
 * A same-named user declaration, alias, or import disables that correction because its result type is unknown.
 */
class NonNullAssertion :
    Rule(
        ruleId = RuleId("harness:non-null-assertion"),
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
                val redundantGuard: KtCallExpression? =
                    (postfixExpression.baseExpression as? KtCallExpression)
                        ?.takeIf { call -> isStandardGuardCall(call) }
                emit(
                    node.startOffset,
                    "avoid non-null assertion `${KtTokens.EXCLEXCL.value}`; use safe call (${KtTokens.SAFE_ACCESS.value}), " +
                        "Elvis (${KtTokens.ELVIS.value}), or an explicit `requireNotNull` or `checkNotNull` guard",
                    redundantGuard !== null
                ).ifAutocorrectAllowed {
                    redundantGuard?.let { guard ->
                        postfixExpression.node.replaceWith(
                            KtPsiFactory
                                .contextual(postfixExpression, false)
                                .createExpression(guard.text)
                                .node
                        )
                    }
                }
            }
        }
    }

    private fun isStandardGuardCall(call: KtCallExpression): Boolean =
        (call.calleeExpression as? KtNameReferenceExpression)
            ?.getReferencedName()
            ?.takeIf { name -> name in GUARD_FUNCTIONS }
            ?.let { name -> (call.containingFile as? KtFile)?.let { file -> !file.shadows(name) } } == true

    private fun KtFile.shadows(name: String): Boolean =
        importDirectives.any { directive ->
            directive.isAllUnder ||
                directive.aliasName == name ||
                (
                    directive.aliasName === null &&
                        directive.importedFqName?.let { fqName ->
                            fqName.parent().asString() != "kotlin" && fqName.shortName().asString() == name
                        } == true
                )
        } ||
            PsiTreeUtil.findChildrenOfType(this, KtNamedDeclaration::class.java).any { declaration -> declaration.name == name }
}
