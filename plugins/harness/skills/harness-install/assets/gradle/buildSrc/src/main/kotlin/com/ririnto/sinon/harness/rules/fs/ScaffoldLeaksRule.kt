package com.ririnto.sinon.harness.rules.fs

import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * Rule that forbids scaffold placeholder patterns in active assets.
 *
 * @category scaffoldLeaks
 */
object ScaffoldLeaksRule : HarnessCheckRule() {
    /**
     * Category identifier for scaffold leak findings.
     *
     * Used to reference rule configuration and severity levels in the harness manifest.
     */
    override val category: String = "scaffoldLeaks"

    override fun validate(ctx: RuleContext): Collection<Finding> = buildList {
        val catObj = ctx.manifest.categoryObject(category)
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val scopeObj = parametersObj?.get("scope")?.jsonObject
        if (catObj != null && parametersObj != null && scopeObj != null) {
            val patterns =
                parametersObj["patterns"]
                    ?.jsonArray
                    ?.filter { patternElem ->
                        val obj = patternElem.jsonObject
                        val pattern = JsonAccess.stringFromObject(obj, "pattern")
                        val label = JsonAccess.stringFromObject(obj, "label")
                        pattern.isNotEmpty() && label.isNotEmpty()
                    }?.map { patternElem ->
                        val obj = patternElem.jsonObject
                        JsonAccess.stringFromObject(obj, "pattern") to JsonAccess.stringFromObject(obj, "label")
                    } ?: emptyList()

            val regexes: List<Pair<Regex, String>> =
                patterns.mapNotNull { (pattern, label) ->
                    runCatching { pattern.toRegex() to label }.getOrNull()
                }
            JsonAccess.stringArrayFromObject(scopeObj, "bases").filter(::isSafeRelativeRoot).flatMap { basePath ->
                val base = (ctx.root / basePath).normalize()
                val walkResult = ctx.walkSafe(base)
                val files = walkResult.paths
                files
                    .filter { file -> file.extension in JsonAccess.stringArrayFromObject(scopeObj, "extensions") }
                    .filter { file ->
                        JsonAccess.stringArrayFromObject(scopeObj, "excludedSubtrees").map { excludedPath -> ctx.root / excludedPath }.none { excludedPath ->
                            file.pathString.startsWith(excludedPath.pathString)
                        }
                    }.flatMap { file ->
                        regexes
                            .filter { patternEntry -> patternEntry.first.containsMatchIn(stripMarkdownCode(file.readText())) }
                            .map { patternEntry ->
                                Finding(
                                    ctx.manifest.severityOf(category),
                                    category,
                                    ctx.manifest.stringValue(category, "default")
                                        .replace("{label}", patternEntry.second)
                                        .replace("{file}", file.relativeTo(ctx.root).invariantSeparatorsPathString)
                                        .takeIf { message -> message.isNotEmpty() }
                                        ?: "${patternEntry.second} in active asset: ${file.relativeTo(ctx.root).invariantSeparatorsPathString}",
                                )
                            }
                    }
            }.forEach { finding ->
                add(finding)
            }
        }
    }

    /**
     * Removes Markdown code blocks and inline code spans before prose-level checks.
     */
    private fun stripMarkdownCode(text: String): String {
        /**
         * Fence parser state.
         */
        data class FenceState(val inFence: Boolean, val marker: String)
        val fenceRegex = "^ {0,3}(`{3,}|~{3,})".toRegex()
        val inlineCodeRegex = "`+[^`\\n]*`+".toRegex()
        return text.lines().fold(FenceState(false, "") to emptyList<String>()) { (state, lines), line ->
            val fenceMatch = fenceRegex.find(line)
            when {
                fenceMatch != null -> {
                    val marker = fenceMatch.groupValues[1].take(1)
                    when {
                        !state.inFence -> FenceState(true, marker) to (lines + "")
                        marker == state.marker -> FenceState(false, "") to (lines + "")
                        else -> state to (lines + "")
                    }
                }
                state.inFence -> state to (lines + "")
                else -> state to (lines + line.replace(inlineCodeRegex, ""))
            }
        }.second.joinToString("\n")
    }

    private fun isSafeRelativeRoot(rootEntry: String): Boolean {
        val path = Path.of(rootEntry)
        return rootEntry.isNotBlank() && !path.isAbsolute && path.none { segment -> segment.pathString == ".." || segment.pathString == "." }
    }

}
