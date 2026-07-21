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
 * Flags every Kotlin non-null assertion operator (`!!`) and rewrites it to an explicit
 * `requireNotNull(...)` guard. When the operand is already wrapped in `requireNotNull(...)`
 * or `checkNotNull(...)`, the redundant `!!` is simply stripped. A user-defined callable in
 * the same file with a guard name (function, property, or import) disables autocorrect so the
 * rewritten call never silently resolves to the user declaration instead of the Kotlin
 * standard library guard.
 */
class NonNullAssertion :
    Rule(
        ruleId = RuleId("code:non-null-assertion"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        val GUARD_FUNCTIONS: Set<String> = setOf("requireNotNull", "checkNotNull")
        val STDLIB_GUARD_PATHS: Set<String> = setOf(
            "kotlin.requireNotNull",
            "kotlin.checkNotNull"
        )
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if (node.elementType === OPERATION_REFERENCE && node.firstChildNode?.elementType === KtTokens.EXCLEXCL) {
            (node.treeParent?.psi as? KtPostfixExpression)?.let { postfixExpression ->
                postfixExpression.baseExpression?.let { baseExpression ->
                    val shadowNames = collectGuardShadows(postfixExpression)
                    val canCorrect = shadowNames.isEmpty()
                    emit(
                        node.startOffset,
                        when (canCorrect) {
                            true -> "avoid non-null assertion `${KtTokens.EXCLEXCL.value}`; use safe call (${KtTokens.SAFE_ACCESS.value}), Elvis (${KtTokens.ELVIS.value}), or an explicit `requireNotNull` guard"
                            else -> "avoid non-null assertion `${KtTokens.EXCLEXCL.value}`; a user-defined `${shadowNames.first()}` shadows the standard library guard, so rewrite it manually with `kotlin.requireNotNull(...)` (assuming no local `kotlin` receiver shadows the package) or rename the shadow"
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

    private fun collectGuardShadows(postfixExpression: KtPostfixExpression): Set<String> {
        val file = postfixExpression.containingFile as? KtFile ?: return emptySet()
        val imports = file.importDirectives
        return when (imports.any { directive -> directive.isAllUnder }) {
            true -> GUARD_FUNCTIONS
            else ->
                PsiTreeUtil.findChildrenOfType(file, KtNamedDeclaration::class.java)
                    .mapNotNull { declaration -> declaration.name }
                    .filter { name -> name in GUARD_FUNCTIONS }
                    .toSet() +
                    imports.asSequence()
                        .filter { directive -> !directive.isAllUnder }
                        .mapNotNull { directive ->
                            val aliasName = directive.aliasName
                            when {
                                aliasName !== null -> if (aliasName in GUARD_FUNCTIONS) aliasName else null
                                else -> {
                                    val name = directive.importedName?.asString() ?: return@mapNotNull null
                                    val path = directive.importPath?.pathStr ?: return@mapNotNull null
                                    if (name in GUARD_FUNCTIONS && path !in STDLIB_GUARD_PATHS) name else null
                                }
                            }
                        }
                        .toSet()
        }
    }
}
