package com.ririnto.sinon.harness.ktlint

import com.ririnto.sinon.harness.core.DefaultManifest
import com.ririnto.sinon.harness.core.DefaultRuleContext
import com.ririnto.sinon.harness.core.Severity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText

/**
 * Verifies the harness Kotlin validation pipeline (base rule, registry, ktlint engine) end to end.
 */
class KtlintEngineTest {
    @Test
    fun reportsNonNullAssertionFinding(
        @TempDir tempDir: Path,
    ) {
        val root = tempDir.toRealPath()
        val sourceDir = root / "src"
        sourceDir.createDirectories()
        (sourceDir / "Foo.kt").writeText("fun f(value: Int?): Boolean = value!! == 1 || 2 > 1\n")
        val scope =
            """
            "enabled": true,
            "parameters": { "sourceRoots": ["src"], "extensions": ["kt"], "includePaths": [], "excludePaths": [] }
            """.trimIndent()
        val manifest =
            Json.parseToJsonElement(
                """
                {
                  "nonNullAssertion": {
                    "description": "test", "severity": "ERROR",
                    "messages": { "default": "avoid non-null assertion `!!` on `{expression}`" },
                    $scope
                  },
                  "greaterThanComparison": {
                    "description": "test", "severity": "ERROR",
                    "messages": { "default": "forbidden `>`/`>=` comparison" },
                    $scope
                  }
                }
                """.trimIndent(),
            ).jsonObject
        val ctx = DefaultRuleContext(root, DefaultManifest(manifest))
        assertEquals(1, ctx.stackSources("nonNullAssertion").size) { "expected one source file under $root" }
        val findings = HarnessKtlintEngine.analyze(ctx)
        val categories = findings.map { finding -> finding.category }.toSet()
        assertTrue(categories.contains("nonNullAssertion")) { "missing nonNullAssertion, got $findings" }
        assertTrue(categories.contains("greaterThanComparison")) { "missing greaterThanComparison (KtFile-root pattern), got $findings" }
        assertTrue(findings.all { finding -> finding.severity == Severity.ERROR }) { "expected all ERROR, got $findings" }
        assertTrue(findings.first { f -> f.category == "nonNullAssertion" }.message.contains("value")) {
            "expected expression token, got $findings"
        }
    }
}
