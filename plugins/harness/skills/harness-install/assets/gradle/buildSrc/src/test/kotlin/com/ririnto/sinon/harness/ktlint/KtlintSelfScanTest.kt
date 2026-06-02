package com.ririnto.sinon.harness.ktlint

import com.ririnto.sinon.harness.core.DefaultManifest
import com.ririnto.sinon.harness.core.DefaultRuleContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Regression check: the ported ktlint rules must keep the already-conformant harness sources clean.
 *
 * The asset's `buildSrc/src/main/kotlin` passed the previous hand-rolled AST engine, so running the
 * ktlint-backed engine over the real manifest must not introduce findings; any finding signals a
 * porting false positive or a convention violation in a new rule file.
 */
class KtlintSelfScanTest {
    @Test
    fun harnessSourcesStayClean() {
        val assetRoot = locateAssetRoot()
        val manifest =
            Json.parseToJsonElement((assetRoot / "docs" / "harness" / "manifest.json").readText()).jsonObject
        val ctx = DefaultRuleContext(assetRoot.toRealPath(), DefaultManifest(manifest))
        val findings = HarnessKtlintEngine.analyze(ctx)
        assertTrue(findings.isEmpty()) {
            "expected no findings on conformant harness sources, got:\n" +
                findings.joinToString("\n") { finding ->
                    "${finding.file}:${finding.startLine} [${finding.severity}] ${finding.category}: ${finding.message}"
                }
        }
    }

    private fun locateAssetRoot(): Path =
        generateSequence(Path.of("").toAbsolutePath()) { path -> path.parent }
            .firstOrNull { path -> (path / "docs" / "harness" / "manifest.json").exists() }
            ?: error("could not locate asset root containing docs/harness/manifest.json")
}
