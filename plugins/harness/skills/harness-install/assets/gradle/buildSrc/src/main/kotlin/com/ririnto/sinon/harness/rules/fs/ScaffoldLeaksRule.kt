package com.ririnto.sinon.harness.rules.fs

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
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

    override fun validate(ctx: RuleContext): Collection<Finding> {
        val parametersObj = (ctx.manifest.categoryObject(category) ?: return emptyList()).get("parameters")?.jsonObject ?: return emptyList()
        val scopeObj = parametersObj.get("scope")?.jsonObject ?: return emptyList()
        val patterns =
            parametersObj["patterns"]
                ?.jsonArray
                ?.filter { patternElem ->
                    val obj = patternElem.jsonObject
                    JsonAccess.stringFromObject(obj, "pattern").let { pattern ->
                        pattern.isNotEmpty() && JsonAccess.stringFromObject(obj, "label").isNotEmpty()
                    }
                }?.map { patternElem ->
                    val obj = patternElem.jsonObject
                    JsonAccess.stringFromObject(obj, "pattern") to JsonAccess.stringFromObject(obj, "label")
                } ?: emptyList()
        val excludedPaths =
            JsonAccess.stringArrayFromObject(scopeObj, "excludedSubtrees").map { excludedPath ->
                ctx.root /
                    excludedPath
            }
        val extensions = JsonAccess.stringArrayFromObject(scopeObj, "extensions")
        return buildList {
            JsonAccess
                .stringArrayFromObject(scopeObj, "bases")
                .filter(::isSafeRelativeRoot)
                .forEach { basePath ->
                    val walkResult = ctx.walkSafe((ctx.root / basePath).normalize())
                    walkResult.paths
                        .filter { file -> file.extension in extensions }
                        .filter { file ->
                            excludedPaths.none { excludedPath ->
                                file.pathString.startsWith(excludedPath.pathString)
                            }
                        }.forEach { file ->
                            patterns.mapNotNull { (pattern, label) ->
                                runCatching { ScaffoldPattern(pattern.toRegex(), label) }.getOrNull()
                            }
                                .filter { patternEntry ->
                                    patternEntry.regex.containsMatchIn(stripMarkdownCode(file.readText()))
                                }.forEach { patternEntry ->
                                    add(
                                        Finding(
                                            ctx.manifest.severityOf(category),
                                            category,
                                            ctx.manifest
                                                .stringValue(category, "default")
                                                .replace("{label}", patternEntry.label)
                                                .replace(
                                                    "{file}",
                                                    file.relativeTo(ctx.root).invariantSeparatorsPathString,
                                                ).takeIf { message -> message.isNotEmpty() }
                                                ?: "${patternEntry.label} in active asset: ${file.relativeTo(
                                                    ctx.root,
                                                ).invariantSeparatorsPathString}",
                                        ),
                                    )
                                }
                        }
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
        data class FenceState(
            val inFence: Boolean,
            val marker: String,
        )
        return text
            .lines()
            .fold(FenceState(false, "") to emptyList<String>()) { (state, lines), line ->
                val fenceMatch = "^ {0,3}(`{3,}|~{3,})".toRegex().find(line)
                when {
                    fenceMatch != null -> {
                        val marker = fenceMatch.groupValues[1].take(1)
                        when {
                            !state.inFence -> FenceState(true, marker) to (lines + "")
                            marker == state.marker -> FenceState(false, "") to (lines + "")
                            else -> state to (lines + "")
                        }
                    }

                    state.inFence -> {
                        state to (lines + "")
                    }

                    else -> {
                        state to (lines + line.replace("`+[^`\\n]*`+".toRegex(), ""))
                    }
                }
            }.second
            .joinToString("\n")
    }

    private fun isSafeRelativeRoot(rootEntry: String): Boolean {
        val path = Path.of(rootEntry)
        return rootEntry.isNotBlank() && !path.isAbsolute &&
            path.none { segment -> segment.pathString == ".." || segment.pathString == "." }
    }

    private data class ScaffoldPattern(
        val regex: Regex,
        val label: String,
    )
}
