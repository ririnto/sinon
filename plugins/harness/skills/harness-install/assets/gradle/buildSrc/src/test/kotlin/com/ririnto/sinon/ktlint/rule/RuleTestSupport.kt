package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.EditorConfigDefaults
import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import java.nio.file.FileSystems
import java.nio.file.Files
import kotlin.io.path.writeText

internal fun lintRule(
    ruleProvider: RuleProvider,
    source: String,
    editorConfig: String? = null,
    fileName: String = "Sample.kt"
): List<LintError> {
    val directory = createEditorConfigDirectory(editorConfig, fileName)
    return try {
        val code = createCode(source, directory, fileName)
        buildEngine(ruleProvider).let { engine ->
            buildList {
                engine.lint(code) { lintError -> add(lintError) }
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

private fun createEditorConfigDirectory(editorConfig: String?, fileName: String): java.nio.file.Path? =
    if (editorConfig !== null || fileName != "Sample.kt") {
        Files.createTempDirectory("ktlint-rule-test").also { temporaryDirectory ->
            editorConfig?.let { config -> temporaryDirectory.resolve(".editorconfig").writeText(config) }
        }
    } else {
        null
    }

private fun createCode(source: String, directory: java.nio.file.Path?, fileName: String): Code =
    if (directory === null) {
        Code.fromSnippet(source)
    } else {
        Code.fromSnippetWithPath(source, directory.resolve(fileName))
    }

private fun deleteDirectory(directory: java.nio.file.Path?) {
    directory?.let { temporaryDirectory ->
        Files.walk(temporaryDirectory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { path -> Files.deleteIfExists(path) }
        }
    }
}

private fun buildEngine(ruleProvider: RuleProvider): KtLintRuleEngine =
    KtLintRuleEngine(
        ruleProviders = setOf(ruleProvider),
        editorConfigDefaults = EditorConfigDefaults.EMPTY_EDITOR_CONFIG_DEFAULTS,
        editorConfigOverride = EditorConfigOverride.EMPTY_EDITOR_CONFIG_OVERRIDE,
        isInvokedFromCli = false,
        fileSystem = FileSystems.getDefault()
    )
