package ai.harness.gradle.rules

import ai.harness.gradle.HarnessPsiResults.Finding
import ai.harness.gradle.HarnessCheck
import ai.harness.gradle.Severity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.relativeTo

/**
 * Rule that forbids disallowed symlinks at the root level.
 */
object SymlinkSafetyRule : HarnessCheckRule() {
    override val category: String = "symlinkSafety"
    override fun applies(manifest: JsonObject): Boolean =
        manifest[category]?.jsonObject?.get("enabled")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: true

    override fun validate(
        manifest: JsonObject,
        root: Path,
    ): Collection<Finding> {
        val catObj = manifest[category]?.jsonObject
        val parametersObj = catObj?.get("parameters")?.jsonObject
        val messagesObj = catObj?.get("messages")?.jsonObject
        return when {
            catObj == null || parametersObj == null || messagesObj == null -> {
                emptyList()
            }

            else -> {
                val allowedPairs =
                    parametersObj["allowedSymlinkPairs"]
                        ?.jsonArray
                        ?.filter { pairElem ->
                            val pair = pairElem.jsonArray
                            2 <= pair.size && pair[0].jsonPrimitive.contentOrNull != null &&
                                pair[1].jsonPrimitive.contentOrNull != null
                        }?.map { pairElem ->
                            val pair = pairElem.jsonArray
                            val a = pair[0].jsonPrimitive.contentOrNull!!
                            val b = pair[1].jsonPrimitive.contentOrNull!!
                            a to b
                        } ?: emptyList()
                val allowed = (allowedPairs.flatMap { (a, b) -> listOf(a to b, b to a) }).toSet()

                val rootFiles =
                    try {
                        root.listDirectoryEntries()
                    } catch (e: Exception) {
                        emptyList()
                    }
                rootFiles
                    .filter { file ->
                        val target =
                            try {
                                file.readSymbolicLink().toString()
                            } catch (error: Exception) {
                                error.localizedMessage?.let { "" } ?: ""
                            }
                        file.isSymbolicLink() && (file.name to target) !in allowed
                    }.map { file ->
                        Finding(
                            Severity.ERROR,
                            category,
                            HarnessCheck.stringFrom(messagesObj, "fileNotAllowed").takeIf { message ->
                                message.isNotEmpty()
                            }
                                ?: "symlink file is not allowed: ${file.relativeTo(root)}",
                        )
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
