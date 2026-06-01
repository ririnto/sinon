package com.ririnto.sinon.harness.rules.fs

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.core.Severity
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.div
import kotlin.io.path.isDirectory

/**
 * Rule that requires .gitkeep or real files in empty directories.
 */
object EmptyDirectoryPlaceholdersRule : HarnessCheckRule() {
    /**
     * Category identifier for this rule.
     */
    override val category = "emptyDirectoryPlaceholders"

    override fun validate(ctx: RuleContext): Collection<Finding> =
        buildList {
            val parametersObj = ctx.manifest.categoryObject(category)?.get("parameters")?.jsonObject ?: return@buildList

            val categorySeverity = ctx.manifest.severityOf(category)
            val pathSeverity = Severity.ERROR
            JsonAccess
                .stringArrayFromObject(parametersObj, "directories")
                .forEach { dirPath ->
                    val directory = SafeDirectoryPath.resolve(ctx.root, dirPath, pathSeverity) { finding ->
                        add(finding)
                    } ?: return@forEach
                    if (!directory.isDirectory()) {
                        return@forEach
                    }

                    if (IsEmptyDirectory.check(directory) && !HasGitkeepPlaceholder.check(directory)) {
                        add(
                            Finding(
                                categorySeverity,
                                category,
                                "empty directory must keep placeholder or real files: $dirPath",
                            ),
                        )
                    }
                }
        }

    /**
     * Creates .gitkeep files in empty directories that lack placeholders.
     */
    override fun format(ctx: RuleContext): Collection<Path> =
        buildList {
            val parametersObj = ctx.manifest.categoryObject(category)?.get("parameters")?.jsonObject ?: return@buildList

            val pathSeverity = Severity.ERROR
            JsonAccess.stringArrayFromObject(parametersObj, "directories").forEach { dirPath ->
                val directory = SafeDirectoryPath.resolve(ctx.root, dirPath, pathSeverity) { _ ->
                    // no-op: format mode should only emit paths
                } ?: return@forEach
                if (!directory.isDirectory()) {
                    return@forEach
                }
                val gitkeepPath = directory / ".gitkeep"

                if (!IsContainedByRoot.check(ctx.root, gitkeepPath)) {
                    return@forEach
                }

                if (IsEmptyDirectory.check(directory) && !HasGitkeepPlaceholder.check(directory)) {
                    gitkeepPath.createFile()
                    add(gitkeepPath)
                }
            }
        }
}
