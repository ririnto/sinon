package com.ririnto.sinon.harness.rules.fs

import com.ririnto.sinon.harness.core.JsonAccess
import com.ririnto.sinon.harness.core.RuleContext
import com.ririnto.sinon.harness.rules.HarnessCheckRule
import com.ririnto.sinon.harness.ast.HarnessAstResults.Finding
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/**
 * Rule that forbids unchecked tasks in completed plans.
 */
object UncheckedTasksRule : HarnessCheckRule() {
    /**
     * Category key.
     */
    override val category: String = "uncheckedTasks"
    override fun validate(ctx: RuleContext): Collection<Finding> = buildList {
        val catObj = ctx.manifest.categoryObject(category)
        val parametersObj = catObj?.get("parameters")?.jsonObject
        if (catObj != null && parametersObj != null) {
            if ((ctx.root / JsonAccess.stringFromObject(parametersObj, "directory")).isDirectory()) {
                ctx
                    .walkSafe(ctx.root / JsonAccess.stringFromObject(parametersObj, "directory"))
                    .paths
                    .filter { file ->
                        file.name.endsWith(".md")
                    }
                    .filter { file ->
                        JsonAccess.stringFromObject(parametersObj, "uncheckedTaskPattern").toRegex().containsMatchIn(file.readText())
                    }
                    .forEach { file ->
                        add(
                            Finding(
                                ctx.manifest.severityOf(category),
                                category,
                                ctx.manifest.stringValue(category, "default").takeIf { message -> message.isNotEmpty() }
                                    ?: "completed plan has unchecked tasks: ${file.relativeTo(ctx.root)}",
                            ),
                        )
                    }
            }
        }
    }

}
