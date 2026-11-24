package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.isWhiteSpace20
import com.pinterest.ktlint.rule.engine.core.api.prevLeaf
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * Forbids ordinary line and block comments throughout Kotlin source.
 *
 * KDoc and shebangs are outside this rule's token set.
 * Required formatter directives, suppressions, and notices remain allowed.
 */
class NoLineComment :
    Rule(
        ruleId = RuleId("harness:no-line-comment"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        const val FORMATTER_DIRECTIVE_PREFIX: String = "@formatter:"
        val APACHE_COPYRIGHT_LINE: Regex = """Copyright(?: \(c\))? +[0-9]{4}(?:[,-] *[0-9]{4})* +\S.*""".toRegex(RegexOption.IGNORE_CASE)
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        if ((node.elementType == KtTokens.EOL_COMMENT || node.elementType == KtTokens.BLOCK_COMMENT) &&
            !node.isRequiredComment()
        ) {
            emit(
                node.psi.textOffset,
                "Keep required notices. Put discretionary rationale in KDoc on the declaration.",
                false
            )
        }
    }

    private fun ASTNode.isRequiredComment(): Boolean =
        text
            .removePrefix("//")
            .removePrefix("/*")
            .trimStart()
            .let { value ->
                value.startsWith(FORMATTER_DIRECTIVE_PREFIX) ||
                    """^(?:ktlint|noinspection)\b|^!?\s*\*?\s*(?:@license\b|@preserve\b|SPDX-License-Identifier:|\bcopyright\b)"""
                        .toRegex(
                            RegexOption.IGNORE_CASE
                        ).containsMatchIn(value) ||
                    isOpeningApacheLicenseBlock(value)
            }

    private fun ASTNode.isOpeningApacheLicenseBlock(value: String): Boolean {
        if (elementType != KtTokens.BLOCK_COMMENT ||
            "/*" in value ||
            prevLeaf { leaf -> 0 < leaf.textLength && !leaf.isWhiteSpace20 && leaf.elementType != KtTokens.SHEBANG_COMMENT } !== null
        ) {
            return false
        }
        val lines = value.removeSuffix("*/").lineSequence().map { line -> line.trimStart().removePrefix("*").trim() }
        return lines.any(APACHE_COPYRIGHT_LINE::matches) &&
            lines.any { line -> line == """Licensed under the Apache License, Version 2.0 (the "License");""" }
    }
}
