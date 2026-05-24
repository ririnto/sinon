package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.HarnessCheck
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * Rule that forbids scaffold placeholder patterns in active assets.
 */
object ScaffoldLeaksRule : HarnessCheckRule() {
    override val category: String = "scaffoldLeaks"
    /**
     * Removes Markdown code blocks and inline code spans before prose-level checks.
     */
    private fun stripMarkdownCode(text: String): String {
        var inFence = false
        var fenceMarker = ""
        return text
            .lines()
            .joinToString("\n") { line ->
                val fenceMatch = Regex("^ {0,3}(`{3,}|~{3,})").find(line)
                when {
                    fenceMatch != null -> {
                        val marker = fenceMatch.groupValues[1].take(1)
                        if (!inFence) {
                            inFence = true
                            fenceMarker = marker
                        } else if (marker == fenceMarker) {
                            inFence = false
                        }
                        ""
                    }

                    inFence -> ""
                    else -> line.replace(Regex("`+[^`\\n]*`+"), "")
                }
            }
    }

    private fun isSafeRelativeRoot(rootEntry: String): Boolean {
        val path = Path.of(rootEntry)
        return rootEntry.isNotBlank() && !path.isAbsolute && path.none { segment -> segment.toString() == ".." }
    }

    override fun applies(manifest: JsonObject): Boolean =
        manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> {
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        val scopeObj = parametersObj?.get("scope")?.jsonObject
        return when {
            catObj == null || parametersObj == null || messagesObj == null || scopeObj == null -> {
                emptyList()
            }

            else -> {
                val bases = HarnessCheck.stringArrayFrom(scopeObj, "bases")
                val excludedSubtrees = HarnessCheck.stringArrayFrom(scopeObj, "excludedSubtrees")
                val extensions = HarnessCheck.stringArrayFrom(scopeObj, "extensions")
                val patterns =
                    parametersObj["patterns"]
                        ?.jsonArray
                        ?.filter { patternElem ->
                            val obj = patternElem.jsonObject
                            val pattern = HarnessCheck.stringFrom(obj, "pattern")
                            val label = HarnessCheck.stringFrom(obj, "label")
                            pattern.isNotEmpty() && label.isNotEmpty()
                        }?.map { patternElem ->
                            val obj = patternElem.jsonObject
                            val pattern = HarnessCheck.stringFrom(obj, "pattern")
                            val label = HarnessCheck.stringFrom(obj, "label")
                            pattern to label
                        } ?: emptyList()
                val excludedPaths = excludedSubtrees.map { excludedPath -> root / excludedPath }

                /**
                 * Filter out patterns that don't compile; invalid regexes silently fail without raising findings.
                 */
                val regexes =
                    patterns
                        .filter { (pattern, label) ->
                            try {
                                pattern.toRegex()
                                true
                            } catch (e: Exception) {
                                false
                            }
                        }.map { (pattern, label) ->
                            pattern.toRegex() to label
                        }
                bases.filter(::isSafeRelativeRoot).flatMap { basePath ->
                    val base = (root / basePath).normalize()
                    val files = HarnessCheck.walkSafe(root, base).first
                    files
                        .filter { file -> file.extension in extensions }
                        .filter { file ->
                            excludedPaths.none { excludedPath ->
                                file.toString().startsWith(excludedPath.toString())
                            }
                        }.flatMap { file ->
                            val text = stripMarkdownCode(file.readText())
                            regexes
                                .filter { patternEntry -> patternEntry.first.containsMatchIn(text) }
                                .map { patternEntry ->
                                    val label = patternEntry.second
                                    Finding(
                                        HarnessCheck.severityOf(manifest, category),
                                        category,
                                        HarnessCheck
                                            .stringFrom(messagesObj, "default")
                                            .replace("{label}", label)
                                            .replace("{file}", file.relativeTo(root).toString())
                                            .takeIf { message -> message.isNotEmpty() }
                                            ?: "$label in active asset: ${file.relativeTo(root)}",
                                    )
                                }
                        }
                }
            }
        }
    }

    override fun renderPsiFindings(
        findings: List<ai.harness.gradle.PsiFinding>,
        manifest: JsonObject,
    ): Collection<Finding> = emptyList()

    override fun findPsiFindings(
        file: Path,
        root: Path,
        psiFactory: org.jetbrains.kotlin.psi.KtPsiFactory?,
    ): List<ai.harness.gradle.PsiFinding> = emptyList()
}
