package com.ririnto.sinon.harness.ast

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * Shared PSI utilities for rule-owned source scanners.
 */
object AstSupport {
    /**
     * Parse a Kotlin source file with the provided PSI factory.
     */
    fun parse(
        file: Path,
        psiFactory: KtPsiFactory?,
    ): KtFile? = psiFactory?.createFile("temp", file.readText())

    /**
     * Convert a source file path to a repository-relative display path.
     */
    fun relativeFilePath(
        file: Path,
        root: Path,
    ): String = file.relativeTo(root).invariantSeparatorsPathString

    /**
     * Resolve a PSI offset to a one-based line number.
     */
    fun lineOf(
        ktFile: KtFile,
        offset: Int?,
    ): Int = (ktFile.viewProvider.document?.getLineNumber(offset ?: 0) ?: 0) + 1

    /**
     * Return true when this PSI element has a descendant of the requested type.
     */
    inline fun <reified T : PsiElement> PsiElement.hasDescendantOfType(
        crossinline predicate: (T) -> Boolean = { true },
    ): Boolean =
        generateSequence(listOf(this)) { layer ->
            layer.flatMap { node -> node.children.toList() }.takeIf { nodes -> nodes.isNotEmpty() }
        }.flatten()
            .any { element -> element is T && predicate(element) }
}
