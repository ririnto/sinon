package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import java.nio.file.Path

/**
 * Rule that requires documentation files to contain specified headings.
 *
 * @category docHeadings
 */
object DocHeadingsRule : HarnessCheckRule() {
    /**
     * Category identifier for this rule.
     */
    override val category: String = "docHeadings"
    private val markdownParser: Parser = Parser.builder().build()

    override fun validate(ctx: RuleContext): Collection<Finding> {
        val parametersObj =
            ctx.manifest
                .categoryObject(category)
                ?.get("parameters")
                ?.jsonObject ?: return emptyList()
        val sourceFilterObj = parametersObj["sourceFilter"]?.jsonObject
        return JsonAccess
            .stringArrayFromObject(
                referencedCategoryParameters(ctx, parametersObj),
                "paths",
            ).filter { sourceFile -> sourceFile.startsWith(JsonAccess.stringFromObject(sourceFilterObj, "prefix")) }
            .filter { sourceFile -> sourceFile.endsWith(JsonAccess.stringFromObject(sourceFilterObj, "suffix")) }
            .flatMap { docPath ->
                ctx.manifest
                    .stringArray(category, "headings")
                    .filter { heading -> !extractHeadings(markdownParser.parse(ctx.readSafe(docPath))).contains(heading) }
                    .map { heading ->
                        Finding(
                            ctx.manifest.severityOf(category),
                            category,
                            "doc missing $heading: $docPath",
                        )
                    }
            }
    }

    /**
     * Retrieves the parameters object referenced by the sourceFilesFromCategory field.
     *
     * @param ctx Rule context containing manifest data.
     * @param parametersObj Parameters object with sourceFilesFromCategory field.
     * @return Referenced parameters object or null if not found.
     */
    private fun referencedCategoryParameters(
        ctx: RuleContext,
        parametersObj: JsonObject,
    ): JsonObject? {
        return ctx.manifest.raw[JsonAccess.stringFromObject(parametersObj, "sourceFilesFromCategory")]
            ?.jsonObject
            ?.get("parameters")
            ?.jsonObject
    }

    /**
     * Extracts all heading text content from a document node tree.
     *
     * @param node Root node to traverse.
     * @return Set of heading text strings found in the node.
     */
    private fun extractHeadings(node: Node): Set<String> =
        buildSet {
            node.accept(HeadingsCollector(this::add))
        }

    /**
     * Converts all children of this node to a string, joining text and line breaks.
     *
     * @return String representation of node children.
     */
    private fun Node.childrenToString(): String =
        generateSequence(firstChild) { current -> current.next }
            .map { child ->
                when (child) {
                    is Text -> child.literal
                    is SoftLineBreak -> " "
                    is HardLineBreak -> " "
                    else -> ""
                }
            }.joinToString("")

    /**
     * Visitor for extracting heading text from Markdown nodes.
     */
    private class HeadingsCollector(
        private val record: (String) -> Unit,
    ) : AbstractVisitor() {
        override fun visit(heading: Heading) {
            val text = heading.childrenToString()
            if (text.isNotEmpty()) {
                record(text)
            }
        }

        /**
         * Converts all children of this node to a string, joining text and line breaks.
         */
        private fun Node.childrenToString(): String =
            generateSequence(firstChild) { current -> current.next }
                .map { child ->
                    when (child) {
                        is Text -> child.literal
                        is SoftLineBreak -> " "
                        is HardLineBreak -> " "
                        else -> ""
                    }
                }.joinToString("")
    }
}
