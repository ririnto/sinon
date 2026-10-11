package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.AutocorrectDecision
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.Rule.About
import com.pinterest.ktlint.rule.engine.core.api.RuleAutocorrectApproveHandler
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Verifies the shared literal helpers by reporting their results for every property in a snippet.
 */
class LiteralTypeInferenceTest :
    FunSpec({
        test("infers the type of each literal initializer") {
            describe(
                """
                val text = "text"
                val flag = true
                val letter = 'a'
                val int = 42
                val grouped = 1_000
                val hex = 0xFF
                val binary = 0b101
                val overflow = 3000000000
                val hexOverflow = 0xFFFFFFFFF
                val long = 42L
                val lowerLong = 42l
                val double = 3.14
                val float = 3.14f
                val upperFloat = 3.14F
                val exponent = 1e3
                """.trimIndent() + "\n"
            ) { property ->
                with(LiteralTypeInference) { property.initializer?.inferLiteralType() }.toString()
            } shouldContainExactly
                listOf(
                    "text=String",
                    "flag=Boolean",
                    "letter=Char",
                    "int=Int",
                    "grouped=Int",
                    "hex=Int",
                    "binary=Int",
                    "overflow=Long",
                    "hexOverflow=Long",
                    "long=Long",
                    "lowerLong=Long",
                    "double=Double",
                    "float=Float",
                    "upperFloat=Float",
                    "exponent=Double"
                )
        }

        test("does not infer unsigned unrepresentable or non literal initializers") {
            describe(
                """
                val unsigned = 1u
                val unsignedLong = 1UL
                val tooLarge = 99999999999999999999
                val negative = -1
                val sum = 1 + 2
                val nothing = null
                val call = compute()
                val reference = other
                """.trimIndent() + "\n"
            ) { property ->
                with(LiteralTypeInference) { property.initializer?.inferLiteralType() }.toString()
            } shouldContainExactly
                listOf(
                    "unsigned=null",
                    "unsignedLong=null",
                    "tooLarge=null",
                    "negative=null",
                    "sum=null",
                    "nothing=null",
                    "call=null",
                    "reference=null"
                )
        }

        test("detects multiline raw strings only") {
            describe(
                """
                val single = ${"\"\"\""}one line${"\"\"\""}
                val multiline = ${"\"\"\""}
                    first
                    second
                ${"\"\"\""}
                val escaped = "first\nsecond"
                val plain = "text"
                """.trimIndent() + "\n"
            ) { property ->
                with(LiteralTypeInference) {
                    (property.initializer as? KtStringTemplateExpression)?.isMultilineRawString().toString()
                }
            } shouldContainExactly
                listOf(
                    "single=false",
                    "multiline=true",
                    "escaped=false",
                    "plain=false"
                )
        }

        test("detects a multiline raw string anywhere inside a declaration") {
            describe(
                """
                val nested = listOf(${"\"\"\""}
                    first
                ${"\"\"\""}.trim())
                val none = listOf("first".trim())
                val flat = ${"\"\"\""}flat${"\"\"\""}.trim()
                """.trimIndent() + "\n"
            ) { property ->
                with(LiteralTypeInference) { property.containsMultilineRawString().toString() }
            } shouldContainExactly
                listOf(
                    "nested=true",
                    "none=false",
                    "flat=false"
                )
        }
    }) {
    companion object {
        private fun describe(
            source: String,
            describeProperty: (KtProperty) -> String
        ): List<String> =
            KtLintRuleTestEngine
                .execute(RuleProvider { PropertyProbe(describeProperty) }, source)
                .diagnostics
                .map(KtLintRuleTestEngine.Diagnostic::detail)
    }

    private class PropertyProbe(
        private val describeProperty: (KtProperty) -> String
    ) : Rule(
            ruleId = RuleId("harness:property-probe"),
            about = About()
        ),
        RuleAutocorrectApproveHandler {
        override fun beforeVisitChildNodes(
            node: ASTNode,
            emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> AutocorrectDecision
        ) {
            if (node.treeParent === null) {
                node.psi.collectDescendantsOfType<KtProperty>().forEach { property ->
                    emit(property.textOffset, "${property.name}=${describeProperty(property)}", false)
                }
            }
        }
    }
}
