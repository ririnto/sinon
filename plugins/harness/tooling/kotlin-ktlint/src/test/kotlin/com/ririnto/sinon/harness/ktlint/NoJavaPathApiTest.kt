package com.ririnto.sinon.harness.ktlint

import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class NoJavaPathApiTest :
    FunSpec({
        test("java files import and java only operations are allowed") {
            val source =
                """
                import java.nio.file.Files
                import java.nio.file.Path
                import java.nio.file.StandardCopyOption

                fun copy(source: Path, target: Path) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("equivalent files helpers are flagged across imports and qualifications") {
            val source =
                """
                import java.nio.file.Files as NioFiles
                import java.nio.file.Files.readString as readText
                import java.nio.file.Path

                fun read(path: Path): String {
                    NioFiles.readString(path)
                    readText(path)
                    return java.nio.file.Files.readString(path)
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        6,
                        5,
                        "Use kotlin.io.path APIs for this java.nio.file.Files helper",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        7,
                        5,
                        "Use kotlin.io.path APIs for this java.nio.file.Files helper",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        8,
                        12,
                        "Use kotlin.io.path APIs for this java.nio.file.Files helper",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("package aliases are recognized for files calls") {
            val source =
                """
                import java.nio.file as nio
                import java.nio.file.Path

                fun exists(path: Path): Boolean = nio.Files.exists(path)
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        35,
                        "Use kotlin.io.path APIs for this java.nio.file.Files helper",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("wildcard package imports are recognized for files calls") {
            val source =
                """
                import java.nio.file.*

                fun exists(path: Path): Boolean = Files.exists(path)
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        35,
                        "Use kotlin.io.path APIs for this java.nio.file.Files helper",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("java path type and file helper chain are recognized with type aliases") {
            val source =
                """
                import java.nio.file.Path as NioPath

                class Example(val path: NioPath) {
                    fun isDirectory(): Boolean = this.path.toFile().isDirectory()
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        34,
                        "Use kotlin.io.path APIs instead of File path helpers",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("path names are resolved within their lexical scopes") {
            val source =
                """
                import java.nio.file.Path

                fun verify(path: Path) {
                    path.toFile().exists()
                    path.toFile().mkdirs()
                    path.toFile().listFiles()
                    fun nested(path: String) {
                        path.toFile().exists()
                    }
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        5,
                        "Use kotlin.io.path APIs instead of File path helpers",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        5,
                        5,
                        "Use kotlin.io.path APIs instead of File path helpers",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        6,
                        5,
                        "Use kotlin.io.path APIs instead of File path helpers",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("qualified and wildcard static imports are recognized") {
            val source =
                """
                import java.nio.file.Files.*
                import java.nio.file.Path

                fun check(path: Path): Boolean = exists(path) || isSymbolicLink(path)
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        34,
                        "Use kotlin.io.path APIs for this java.nio.file.Files helper",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        50,
                        "Use kotlin.io.path APIs for this java.nio.file.Files helper",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("java specific files methods remain allowed") {
            val source =
                """
                import java.io.InputStream
                import java.nio.file.Files
                import java.nio.file.Path
                import java.nio.file.StandardCopyOption

                fun copy(source: Path, target: Path) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                    Files.getFileStore(source)
                }

                fun open(path: Path): InputStream = Files.newInputStream(path)
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("filtered file listings remain allowed") {
            val source =
                """
                import java.io.FileFilter
                import java.nio.file.Path

                fun list(path: Path, filter: FileFilter) {
                    path.toFile().listFiles(filter)
                    path.toFile().listFiles { file -> file.name.endsWith(".kt") }
                }
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("path returning chains stay narrow and uri resolve remains allowed") {
            val source =
                """
                import java.net.URI
                import java.nio.file.Path

                fun child(base: Path, uri: URI): Path {
                    base.toUri().resolve("child")
                    uri.resolve("child")
                    return base.toRealPath().resolve("child")
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        7,
                        30,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("java path resolve overloads are flagged on typed path receivers") {
            val source =
                """
                import java.nio.file.Path as NioPath

                fun child(base: NioPath, child: NioPath): NioPath {
                    base.resolve("child")
                    return base.resolve(child)
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        10,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        5,
                        17,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("resolve on unrelated receivers and other java path operations remain allowed") {
            val source =
                """
                import java.nio.file.Path

                class Resolver {
                    fun resolve(child: String): String = child
                }

                fun use(resolver: Resolver, path: Path) {
                    resolver.resolve("child")
                    path.normalize()
                    path.toUri().resolve("child")
                }
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("resolve after known path returning method is flagged") {
            val source =
                """
                import java.nio.file.Path

                fun child(base: Path): Path = base.normalize().resolve("child")
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        3,
                        48,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("flags child resolve calls from supported Path factories and aliases") {
            val source =
                """
                import java.net.URI
                import java.nio.file.Path as NioPath
                import java.nio.file.Paths as NioPaths
                import java.nio.file.Paths.get as fromSegments
                import java.nio.file.Path.of as fromPath
                import kotlin.io.path.Path as makePath

                fun f(uri: URI) {
                    NioPaths.get(".").resolve("x")
                    makePath(".").resolve("x")
                    java.nio.file.Path.of(".").resolve("x")
                    fromSegments(".").resolve("x")
                    fromPath(".").resolve("x")
                    NioPath.of(".").resolve("x")
                    NioPath.of(uri).resolve("x")
                    NioPaths.get(uri).resolve("x")
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        9,
                        23,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        10,
                        19,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        11,
                        32,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        12,
                        23,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        13,
                        19,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        14,
                        21,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        15,
                        21,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        16,
                        23,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("ignores resolve overloads on Path with unrelated parameters") {
            val source =
                """
                import java.nio.file.Path

                fun f(base: Path, enabled: Boolean) = base.resolve("x", enabled).resolve("y")
                """.trimIndent() + "\n"
            val lintResult1 = KtLintRuleTestEngine.execute(ruleProvider, source)
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("ignores resolve calls after a same named Path extension with a different return type") {
            val source =
                """
                import java.net.URI
                import java.nio.file.Path

                fun Path.getName(name: String): URI = TODO()
                fun f(base: Path) = base.getName("x").resolve("y")
                """.trimIndent() + "\n"
            val lintResult1 = KtLintRuleTestEngine.execute(ruleProvider, source)
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("flags resolve for aliased Path types and factories") {
            val source =
                """
                import java.nio.file.Path as NioPath

                fun f() {
                    val base = NioPath.of(".")
                    base.resolve("child")
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        5,
                        10,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("reports resolve calls on Path-returning chains") {
            val source =
                """
                import java.nio.file.LinkOption
                import java.nio.file.Path

                fun child(base: Path, child: Path): Path {
                    base.resolve("x").resolve("y")
                    base.resolve(child).resolve("y")
                    base.toRealPath(LinkOption.NOFOLLOW_LINKS).resolve("x")
                    base.getName(0).resolve("x")
                    base.subpath(0, 1).resolve("x")
                    return base
                }
                """.trimIndent() + "\n"
            val lintResult1 = KtLintRuleTestEngine.execute(ruleProvider, source)
            lintResult1.diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        5,
                        10,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        5,
                        23,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        6,
                        10,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        6,
                        25,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        7,
                        48,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        8,
                        21,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        9,
                        24,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    )
                )
            lintResult1.formattedCode shouldBe source
        }

        test("ignores variadic resolveSibling extension overloads") {
            val source =
                """
                import java.net.URI
                import java.nio.file.Path

                fun Path.resolveSibling(first: String, second: String): URI = TODO()
                fun f(base: Path) = base.resolveSibling("a", "b").resolve("c")
                """.trimIndent() + "\n"
            val lintResult1 = KtLintRuleTestEngine.execute(ruleProvider, source)
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("ignores named-argument and trailing-lambda Path extensions") {
            val source =
                """
                import java.nio.file.Path

                fun Path.resolve(child: String, transform: () -> String): Path = this
                fun first(base: Path) = base.resolve(child = "x", transform = { "y" })
                fun second(base: Path) = base.resolve("x") { "y" }
                """.trimIndent() + "\n"
            val lintResult1 = KtLintRuleTestEngine.execute(ruleProvider, source)
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("infers Path types through direct Java factory aliases") {
            val source =
                """
                import java.nio.file.Path
                import java.nio.file.Path.of as pathOf
                import java.nio.file.Paths.get as fromPath

                val direct = Path.of(".")
                val staticImport = pathOf(".")
                val pathsImport = fromPath(".")
                fun first() = direct.resolve("child")
                fun second() = staticImport.resolve("child")
                fun third() = pathsImport.resolve("child")
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        8,
                        22,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        9,
                        29,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        10,
                        27,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("infers Path types through an aliased Kotlin Path factory") {
            val source =
                """
                import kotlin.io.path.Path as makePath

                val base = makePath(".")
                fun child() = base.resolve("child")
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        4,
                        20,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("ignores custom-argument File helper extensions") {
            val source =
                """
                import java.io.File
                import java.nio.file.Path

                fun Path.toFile(marker: String): File = File(marker)
                fun Path?.toFile(): File = File(".")
                fun File.exists(marker: String): Boolean = marker.isNotEmpty()
                fun File.isDirectory(marker: String): Boolean = marker.isNotEmpty()
                fun File.mkdirs(marker: String): Boolean = marker.isNotEmpty()

                fun inspect(path: Path) {
                    path.toFile().exists("custom")
                    path.toFile().isDirectory("custom")
                    path.toFile().mkdirs("custom")
                    path.toFile("custom").exists()
                    path.toFile.exists()
                }

                fun inspectNullable(path: Path?) {
                    path.toFile().exists()
                }
                """.trimIndent() + "\n"
            val lintResult1 = KtLintRuleTestEngine.execute(ruleProvider, source)
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("restores a typed Path binding after a same-named loop destructuring") {
            val source =
                """
                import java.nio.file.Path

                fun inspect(path: Path) {
                    for ((path, ignored) in listOf("child" to 1)) {
                        path.resolve("ignored")
                    }
                    path.resolve("child")
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        7,
                        10,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("keeps Path bindings visible in the loop range and after the loop") {
            val source =
                """
                import java.nio.file.Path

                fun String.resolve(child: String): String = this + child
                fun makePairs(path: Path): List<Pair<String, Int>> = emptyList()

                fun inspect(path: Path) {
                    for ((path, ignored) in makePairs(path.resolve("range"))) {
                        path.resolve("inside")
                    }
                    path.resolve("after")
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        7,
                        44,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        10,
                        10,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("scopes ordinary loop parameters to the body only") {
            val source =
                """
                import java.nio.file.Path

                fun makePaths(path: Path): List<String> = emptyList()

                fun inspect(path: Path) {
                    for (path in makePaths(path.resolve("range"))) {
                        path.resolve("inside")
                    }
                    path.resolve("after")
                }
                """.trimIndent() + "\n"
            KtLintRuleTestEngine
                .execute(
                    ruleProvider,
                    source
                ).diagnostics shouldContainExactlyInAnyOrder
                listOf(
                    KtLintRuleTestEngine.Diagnostic(
                        6,
                        33,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    ),
                    KtLintRuleTestEngine.Diagnostic(
                        9,
                        10,
                        "Use the kotlin.io.path division operator for Path child paths",
                        canBeAutoCorrected = false
                    )
                )
        }

        test("imported Files class is not matched when a parameter shadows it") {
            val source =
                """
                import java.nio.file.Files

                class CustomFiles {
                    fun exists(): Boolean = true
                }

                fun inspect(Files: CustomFiles) {
                    Files.exists()
                }
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("static Files imports are not matched when a local function shadows them") {
            val source =
                """
                import java.nio.file.Files.exists
                import java.nio.file.Path

                fun inspect(path: Path) {
                    fun exists(path: Path): Boolean = false
                    exists(path)
                }
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("wildcard Files imports are not matched when a local function shadows them") {
            val source =
                """
                import java.nio.file.Files.*
                import java.nio.file.Path

                fun inspect(path: Path) {
                    fun exists(path: Path): Boolean = false
                    exists(path)
                }
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("destructured path names hide outer path bindings") {
            val source =
                """
                import java.nio.file.Path

                fun String.resolve(child: String): String = this + child

                fun inspect(path: Path) {
                    if (true) {
                        val (path, ignored) = "base" to 1
                        path.resolve("child")
                    }
                }
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("destructured lambda names hide outer path and files bindings") {
            val source =
                """
                import java.nio.file.Files.*
                import java.nio.file.Path

                fun String.resolve(child: String): String = this + child

                fun inspect(path: Path, pairs: List<Pair<String, Int>>) {
                    pairs.forEach { (path, ignored) -> path.resolve("child") }
                    listOf(({ _: Path -> false }) to 1).forEach { (exists, ignored) -> exists(path) }
                }
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }

        test("local Java root does not become a fully qualified Files call") {
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    """
                    class JavaNamespace(val nio: NioNamespace)
                    class NioNamespace(val file: FileNamespace)
                    class FileNamespace(val Files: Helpers)
                    class Helpers {
                        fun exists(): Boolean = true
                    }

                    fun inspect(java: JavaNamespace) {
                        java.nio.file.Files.exists()
                    }
                    """.trimIndent() + "\n"
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe
                """
                class JavaNamespace(val nio: NioNamespace)
                class NioNamespace(val file: FileNamespace)
                class FileNamespace(val Files: Helpers)
                class Helpers {
                    fun exists(): Boolean = true
                }

                fun inspect(java: JavaNamespace) {
                    java.nio.file.Files.exists()
                }
                """.trimIndent() + "\n"
        }

        test("unrelated same named types are safe") {
            val source =
                """
                class Files {
                    fun readAllBytes(): ByteArray = byteArrayOf()
                }

                class Path {
                    fun toFile(): Path = this
                    fun exists(): Boolean = true
                }

                fun use(files: Files, path: Path) {
                    files.readAllBytes()
                    path.toFile().exists()
                }
                """.trimIndent() + "\n"
            val lintResult1 =
                KtLintRuleTestEngine.execute(
                    ruleProvider,
                    source
                )
            lintResult1.diagnostics shouldContainExactlyInAnyOrder emptyList()
            lintResult1.formattedCode shouldBe source
        }
    }) {
    companion object {
        private val ruleProvider: RuleProvider = RuleProvider(::NoJavaPathApi)
    }
}
