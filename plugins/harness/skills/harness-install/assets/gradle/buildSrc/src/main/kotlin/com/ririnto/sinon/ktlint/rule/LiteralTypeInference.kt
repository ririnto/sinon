package com.ririnto.sinon.ktlint.rule

import com.pinterest.ktlint.rule.engine.core.api.ElementType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Shared PSI helpers for analyzing literal and string-template expressions across ktlint rules.
 */
internal object LiteralTypeInference {
    /**
     * Infers the Kotlin type name a literal expression would resolve to, or null when the expression is not a recognized literal.
     */
    fun KtExpression.inferLiteralType(): String? =
        when (this) {
            is KtStringTemplateExpression -> {
                String::class.simpleName
            }

            is KtConstantExpression -> {
                val text = text
                when (node.elementType) {
                    ElementType.BOOLEAN_CONSTANT -> {
                        Boolean::class.simpleName
                    }

                    ElementType.CHARACTER_CONSTANT -> {
                        Char::class.simpleName
                    }

                    ElementType.INTEGER_CONSTANT -> {
                        val suffix = text.takeLastWhile { character -> character in "uUlL" }
                        when {
                            suffix.any { character -> character in "uU" } -> {
                                null
                            }

                            suffix.any { character -> character in "lL" } -> {
                                Long::class.simpleName
                            }

                            else -> {
                                val cleaned = text.replace("_", "")
                                val (digits, radix) =
                                    when {
                                        cleaned.startsWith("0x", ignoreCase = true) -> cleaned.substring(2) to 16
                                        cleaned.startsWith("0b", ignoreCase = true) -> cleaned.substring(2) to 2
                                        else -> cleaned to 10
                                    }
                                when {
                                    digits.toIntOrNull(radix) != null -> Int::class.simpleName
                                    digits.toLongOrNull(radix) != null -> Long::class.simpleName
                                    else -> null
                                }
                            }
                        }
                    }

                    ElementType.FLOAT_CONSTANT -> {
                        when {
                            text.endsWith('f', ignoreCase = true) -> Float::class.simpleName
                            else -> Double::class.simpleName
                        }
                    }

                    else -> {
                        null
                    }
                }
            }

            else -> {
                null
            }
        }

    /**
     * Returns true when the template is a multiline raw string literal (`"""..."""` spanning newlines).
     */
    fun KtStringTemplateExpression.isMultilineRawString(): Boolean =
        node.findChildByType(KtTokens.OPEN_QUOTE)?.text == "\"\"\"" &&
            text.contains('\n')

    /**
     * Returns true when the declaration contains a multiline raw string literal.
     */
    fun KtDeclaration.containsMultilineRawString(): Boolean =
        collectDescendantsOfType<KtStringTemplateExpression>().any { template -> template.isMultilineRawString() }
}
