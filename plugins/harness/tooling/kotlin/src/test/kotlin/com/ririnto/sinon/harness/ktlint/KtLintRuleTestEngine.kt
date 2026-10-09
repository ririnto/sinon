package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride.Companion.plus
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EXPERIMENTAL_RULES_EXECUTION_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.RuleExecution
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.createRuleSetExecutionEditorConfigProperty

internal object KtLintRuleTestEngine {
    fun execute(
        ruleProvider: RuleProvider,
        source: String,
        isKotlinScript: Boolean = false,
        editorConfigOverride: EditorConfigOverride = EditorConfigOverride.EMPTY_EDITOR_CONFIG_OVERRIDE
    ): Result {
        val engine =
            KtLintRuleEngine(
                ruleProviders = setOf(ruleProvider),
                editorConfigOverride = editorConfigOverride.enableRuleSet(ruleProvider)
            )
        val code = Code.fromSnippet(source, isKotlinScript)
        return Result(
            diagnostics =
                buildList {
                    engine.lint(code) { error -> add(Diagnostic(error)) }
                },
            formattedCode =
                engine.format(
                    code = code,
                    rerunAfterAutocorrect = false,
                    defaultAutocorrect = false
                ) { AutocorrectDecision.ALLOW_AUTOCORRECT }
        )
    }

    private fun EditorConfigOverride.enableRuleSet(ruleProvider: RuleProvider): EditorConfigOverride {
        val ruleSetProperty = ruleProvider.ruleId.ruleSetId.createRuleSetExecutionEditorConfigProperty()
        return this.plus(
            EXPERIMENTAL_RULES_EXECUTION_PROPERTY to RuleExecution.enabled,
            *listOfNotNull(
                (ruleSetProperty to RuleExecution.enabled).takeIf { properties[ruleSetProperty] === null }
            ).toTypedArray()
        )
    }

    data class Diagnostic(
        val line: Int,
        val col: Int,
        val detail: String,
        val canBeAutoCorrected: Boolean = true
    ) {
        constructor(error: LintError) : this(error.line, error.col, error.detail, error.canBeAutoCorrected)
    }

    data class Result(
        val diagnostics: List<Diagnostic>,
        val formattedCode: String
    )
}
