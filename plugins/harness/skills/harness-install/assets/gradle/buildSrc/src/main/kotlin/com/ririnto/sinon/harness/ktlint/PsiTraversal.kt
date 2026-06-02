package com.ririnto.sinon.harness.ktlint

import org.jetbrains.kotlin.com.intellij.psi.PsiElement

/**
 * Shared PSI tree traversal helpers for harness Kotlin rules that wrap a PSI visitor.
 */
object PsiTraversal {
    /**
     * Return true when this element has a descendant of the requested type matching the predicate.
     */
    inline fun <reified T : PsiElement> PsiElement.hasDescendantOfType(
        crossinline predicate: (T) -> Boolean = { true },
    ): Boolean =
        generateSequence(listOf(this)) { layer ->
            layer.flatMap { node -> node.children.toList() }.takeIf { nodes -> nodes.isNotEmpty() }
        }.flatten()
            .any { element -> element is T && predicate(element) }

    /**
     * Return all descendants of the requested element type matching the predicate.
     */
    inline fun <reified T : PsiElement> PsiElement.descendantsOfType(
        crossinline predicate: (T) -> Boolean = { true },
    ): Sequence<T> =
        generateSequence(listOf(this)) { layer ->
            layer.flatMap { node -> node.children.toList() }.takeIf { nodes -> nodes.isNotEmpty() }
        }.flatten()
            .filter { element -> element is T && predicate(element) }
            .map { element -> element as T }
}
