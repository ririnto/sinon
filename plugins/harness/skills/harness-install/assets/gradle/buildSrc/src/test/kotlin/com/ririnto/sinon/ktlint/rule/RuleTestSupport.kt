package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.EditorConfigDefaults
import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.writeText

internal object RuleTestSupport {
    internal fun lintRule(
        ruleProvider: RuleProvider,
        source: String,
        editorConfig: String? = null,
        fileName: String = "Sample.kt"
    ): List<LintError> {
        val directory = createEditorConfigDirectory(editorConfig, fileName)
        return try {
            buildEngine(ruleProvider).let { engine ->
                buildList {
                    engine.lint(createCode(source, directory, fileName)) { lintError -> add(lintError) }
                }
            }
        } finally {
            deleteDirectory(directory)
        }
    }

    internal fun formatRule(
        ruleProvider: RuleProvider,
        source: String,
        editorConfig: String? = null,
        fileName: String = "Sample.kt"
    ): String {
        val directory = createEditorConfigDirectory(editorConfig, fileName)
        return try {
            buildEngine(ruleProvider).format(
                createCode(source, directory, fileName),
                true,
                false
            ) { AutocorrectDecision.ALLOW_AUTOCORRECT }
        } finally {
            deleteDirectory(directory)
        }
    }

    private fun createEditorConfigDirectory(editorConfig: String?, fileName: String): Path? =
        if (editorConfig !== null || fileName != "Sample.kt") {
            createTempDirectory(prefix = "ktlint-rule-test").also { temporaryDirectory ->
                editorConfig?.let { config -> (temporaryDirectory / ".editorconfig").writeText(config) }
            }
        } else {
            null
        }

    private fun createCode(source: String, directory: Path?, fileName: String): Code =
        when (directory) {
            null -> Code.fromSnippet(source)
            else -> Code.fromSnippetWithPath(source, directory / fileName)
        }

    @OptIn(ExperimentalPathApi::class)
    private fun deleteDirectory(directory: Path?) {
        directory?.deleteRecursively()
    }

    private fun buildEngine(ruleProvider: RuleProvider): KtLintRuleEngine =
        KtLintRuleEngine(
            ruleProviders = setOf(ruleProvider),
            editorConfigDefaults = EditorConfigDefaults.EMPTY_EDITOR_CONFIG_DEFAULTS,
            editorConfigOverride = EditorConfigOverride.EMPTY_EDITOR_CONFIG_OVERRIDE,
            isInvokedFromCli = false,
            fileSystem = FileSystems.getDefault()
        )
}
