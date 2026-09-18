package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Flags `java.nio.file` APIs where Kotlin path helpers should be used instead.
 *
 * The `java.nio.file.Files` import and `File`-based path checks move to `kotlin.io.path`.
 */
class NoJavaPathApi :
    Rule(
        ruleId = RuleId("harness:no-java-path-api"),
        about = About()
    ),
    RuleAutocorrectApproveHandler {
    private companion object {
        val FILE_PATH_HELPERS: Set<String> =
            setOf(
                ".toFile().isDirectory",
                ".toFile().exists()",
                ".toFile().mkdirs()",
                ".toFile().listFiles()"
            )
    }

    override fun beforeVisitChildNodes(
        node: ASTNode,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
    ) {
        when (val psi = node.psi) {
            is KtImportDirective -> {
                if (psi.importPath?.pathStr == "java.nio.file.Files") {
                    emit(psi.textOffset, "Use kotlin.io.path APIs instead of java.nio.file.Files helpers", false)
                }
            }

            is KtDotQualifiedExpression -> {
                val pathNames = (psi.containingFile as? KtFile)?.javaPathNames() ?: emptySet()
                if (
                    FILE_PATH_HELPERS.any(psi.text::endsWith) &&
                    psi.javaPathReceiverName()?.let { name -> name in pathNames } == true
                ) {
                    emit(psi.textOffset, "Use kotlin.io.path APIs instead of File path helpers", false)
                }
            }
        }
    }

    private fun KtFile.javaPathNames(): Set<String> {
        val importedPathNames =
            importDirectives
                .filter { directive ->
                    directive.importPath?.pathStr == "java.nio.file.Path" ||
                        directive.importPath?.pathStr == "java.nio.file.*"
                }.map { directive -> directive.aliasName ?: "Path" }
                .toSet()
        return importedPathNames +
            collectDescendantsOfType<KtParameter>()
                .mapNotNull { parameter ->
                    parameter.name?.takeIf {
                        parameter.typeReference?.text == "java.nio.file.Path" ||
                            parameter.typeReference?.text in importedPathNames
                    }
                }.toSet() +
            collectDescendantsOfType<KtProperty>()
                .mapNotNull { property ->
                    property.name?.takeIf {
                        property.typeReference?.text == "java.nio.file.Path" ||
                            property.typeReference?.text in importedPathNames
                    }
                }.toSet()
    }

    private fun KtQualifiedExpression.javaPathReceiverName(): String? =
        generateSequence(receiverExpression) { expression ->
            (expression as? KtQualifiedExpression)?.receiverExpression
        }.lastOrNull()?.let { expression ->
            (expression as? KtNameReferenceExpression)?.getReferencedName()
        }
}
