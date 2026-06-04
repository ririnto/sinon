package com.ririnto.sinon.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EditorConfigProperty
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtFile

/**
 * Base class for Harness custom ktlint rules.
 *
 * Restricts every Harness rule to Kotlin source files by skipping Kotlin script files such as
 * `build.gradle.kts` and `settings.gradle.kts`, so build scripts are linted only by the standard
 * rule set. Subclasses implement [visitNode] instead of overriding the node visitor directly.
 */
abstract class KtlintRule(
    ruleId: RuleId,
    usesEditorConfigProperties: Set<EditorConfigProperty<*>> = emptySet(),
) : Rule(
        ruleId = ruleId,
        about = About(),
        usesEditorConfigProperties = usesEditorConfigProperties,
    ),
    RuleAutocorrectApproveHandler {
    private var skipFile = false

    final override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    ) {
        (node.psi as? KtFile)?.let { ktFile -> skipFile = ktFile.isScript() }
        if (skipFile) {
            return
        }
        visitNode(node, emit)
    }

    /**
     * Visits a node of a non-script Kotlin source file.
     */
    protected abstract fun visitNode(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision,
    )
}
