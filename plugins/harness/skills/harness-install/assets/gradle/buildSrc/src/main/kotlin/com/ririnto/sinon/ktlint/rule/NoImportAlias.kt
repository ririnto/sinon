package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtImportAlias
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

/**
 * Flags `import ... as Foo` directives where the alias text matches the imported simple name (e.g.
 * `import a.Foo as Foo`); the alias is redundant and should be removed. Collision detection across
 * same-package declarations or sibling imports is intentionally omitted because a single-file rule
 * cannot reliably resolve same-package symbols that are reachable without an explicit import.
 */
class NoImportAlias :
    Rule(
        ruleId = RuleId("code:no-import-alias"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        (node.psi as? KtImportDirective)?.let { directive ->
            directive.aliasName?.let { text ->
                directive.importedFqName?.shortName()?.asString()?.let { name ->
                    directive.getChildOfType<KtImportAlias>()?.let { alias ->
                        if (text == name) {
                            emit(
                                alias.textOffset,
                                "import alias `$text` duplicates the imported simple name; remove the alias",
                                false
                            )
                        }
                    }
                }
            }
        }
    }
}
