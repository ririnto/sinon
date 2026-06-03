package com.ririnto.sinon.harness.ktlint

import com.ririnto.sinon.harness.core.DefaultManifest
import com.ririnto.sinon.harness.core.DefaultRuleContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText

/**
 * Positive coverage: each harness Kotlin rule must actually fire on a deliberately violating snippet.
 *
 * This guards against false-negative porting bugs that the clean self-scan cannot detect.
 */
class KtlintPositiveTest {
    @Test
    fun everyRuleFiresOnAViolation(
        @TempDir tempDir: Path,
    ) {
        val root = tempDir.toRealPath()
        val sourceDir = root / "src"
        sourceDir.createDirectories()
        val manifest = allRulesManifest()
        val cases = violationCases()
        val failures =
            cases.mapNotNull { (category, code) ->
                (sourceDir / "Sample.kt").writeText(code)
                val ctx = DefaultRuleContext(root, DefaultManifest(manifest))
                val found = HarnessKtlintEngine.analyze(ctx).map { finding -> finding.category }.toSet()
                if (found.contains(category)) {
                    null
                } else {
                    "$category did not fire; categories found = $found"
                }
            }
        assertTrue(failures.isEmpty()) { "rules that failed to fire:\n${failures.joinToString("\n")}" }
    }

    private fun allRulesManifest(): JsonObject {
        val categories =
            listOf(
                "leafFunctionBlankLines", "implicitLambdaIt",
                "kotlinTopLevelDeclarationCount", "ifStatementBraces",
                "terminalBranchWhen", "nonNullAssertion",
                "uncheckedCastSuppression", "unstructuredLogging", "importOverFqn", "publicDeclarationDocComment",
                "leadingUnderscore", "multilineDocStyle", "companionObjectPosition",
            )
        val body =
            categories.joinToString(",\n") { category ->
                """
                "$category": {
                  "description": "t", "enabled": true, "severity": "ERROR",
                  "messages": { "default": "violation {snippet}{name}{import}{expression}{function}{context}{position}" },
                  "parameters": { "sourceRoots": ["src"], "extensions": ["kt"], "includePaths": [], "excludePaths": [] }
                }
                """.trimIndent()
            }
        return Json.parseToJsonElement("{ $body }").jsonObject
    }

    private fun violationCases(): List<Pair<String, String>> =
        listOf(
            "nonNullAssertion" to "fun f(x: Int?): Int = x!!\n",
            "ifStatementBraces" to "fun f(c: Boolean) { if (c) g() }\nfun g() {}\n",
            "terminalBranchWhen" to "fun f(c: Boolean): Int = if (c) 1 else 2\n",
            "leafFunctionBlankLines" to "fun f() {\n    val a = 1\n\n\n    val b = a\n}\n",
            "importOverFqn" to "fun f(): java.util.UUID? = null\n",
            "leadingUnderscore" to "val _x = 1\n",
            "unstructuredLogging" to "fun f() { println(\"x\") }\n",
            "uncheckedCastSuppression" to "@Suppress(\"UNCHECKED_CAST\")\nfun f() {}\n",
            "implicitLambdaIt" to "fun f(xs: List<Int>): List<Int> = xs.map { it + 1 }\n",
            "kotlinTopLevelDeclarationCount" to "class A\nclass B\n",
            "companionObjectPosition" to "class A {\n    fun g() {}\n\n    companion object {}\n}\n",
            "publicDeclarationDocComment" to "class Undocumented\n",
            "multilineDocStyle" to "/** single line */\nclass A\n",
        )
}
