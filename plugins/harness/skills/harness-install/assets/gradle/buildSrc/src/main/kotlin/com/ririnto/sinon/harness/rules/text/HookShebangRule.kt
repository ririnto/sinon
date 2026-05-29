package com.ririnto.sinon.harness.rules.text

import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

/**
 * Rule that requires hooks to have correct shebang.
 *
 * Operates on plain text; the check targets a fixed-position single line and requires no AST parser.
 */
object HookShebangRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "hookShebang"

    override fun validate(ctx: RuleContext): Collection<Finding> {
        val parametersObj = (ctx.manifest.categoryObject(category) ?: return emptyList())
            .get("parameters")?.jsonObject
            ?: return emptyList()
        val hooks = JsonAccess.stringArrayFromObject(parametersObj, "hooks")
        val expectedShebang = JsonAccess.stringFromObject(parametersObj, "expectedShebang")
        return buildList {
            addAll(
                hooks
                    .filter { hookPath ->
                        val hook = safeHookPath(ctx.root, hookPath) ?: return@filter false
                        (hook.readLines().firstOrNull() ?: "") != expectedShebang
                    }.map { hookPath ->
                        Finding(
                            ctx.manifest.severityOf(category),
                            category,
                            ctx.manifest.stringValue(category, "default").takeIf { message ->
                                message.isNotEmpty()
                            }
                                ?: "$hookPath must start with ${JsonAccess.stringFromObject(
                                    parametersObj,
                                    "expectedShebang",
                                )}",
                        )
                    },
            )
        }
    }

    override fun format(ctx: RuleContext): Collection<Path> {
        val parametersObj = (ctx.manifest.categoryObject(category) ?: return emptyList())
            .get("parameters")?.jsonObject
            ?: return emptyList()
        val expectedShebang = JsonAccess.stringFromObject(parametersObj, "expectedShebang")
        val hooks = JsonAccess.stringArrayFromObject(parametersObj, "hooks")
        return buildList {
            hooks
                .forEach { hookPath ->
                    val hook = safeHookPath(ctx.root, hookPath) ?: return@forEach
                    val currentText = hook.readText()
                    val currentFirstLine = currentText.lines().firstOrNull() ?: ""
                    if (currentFirstLine != expectedShebang) {
                        hook.writeText("$expectedShebang\n$currentText")
                        add(hook)
                    }
                }
        }
    }
}

/**
 * Validates that a hook path is safe for read/write operations.
 *
 * Rejects absolute paths, traversal segments, leading-dash components,
 * symlink components, and paths that resolve outside the root.
 *
 * @param root the project root directory.
 * @param hookPath the hook path string from manifest.
 * @return the resolved Path if safe, null otherwise.
 */
private fun safeHookPath(root: Path, hookPath: String): Path? {
    val path = Path.of(hookPath)
    if (path.isAbsolute) return null
    if (path.nameCount == 0) return null
    if ((0..<path.nameCount).any { segment ->
        val s = path.getName(segment).pathString
        s == ".." || s == "." || s.startsWith("-")
    }) return null
    val resolved = root / path
    if (!resolved.isRegularFile()) return null
    if (hasSymlinkComponent(root, resolved)) return null
    return try {
        val rootReal = root.toRealPath()
        val resolvedReal = resolved.toRealPath()
        if (resolvedReal.startsWith(rootReal)) resolvedReal else null
    } catch (_: Exception) {
        null
    }
}

/**
 * Checks whether any path segment between root and target is a symbolic link.
 */
private fun hasSymlinkComponent(root: Path, target: Path): Boolean {
    val relative = try { target.relativeTo(root) } catch (_: Exception) { return true }
    var current = root
    for (segmentIndex in 0 until relative.nameCount) {
        val segment = relative.getName(segmentIndex)
        if (segment.pathString == ".") continue
        if (segment.pathString == "..") return true
        current /= segment
        if (java.nio.file.Files.isSymbolicLink(current)) return true
    }
    return false
}
